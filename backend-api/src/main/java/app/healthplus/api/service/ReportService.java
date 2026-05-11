package app.healthplus.api.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 健康报告生成服务 —— 根据用户上传的健康数据生成周报或月报。
 *
 * <p>设计思路：
 * <ul>
 *   <li>结合 {@link HealthStateClassifier} 对每日健康状态进行分类（优/良/中/差），
 *       统计报告周期内的状态分布</li>
 *   <li>从聚合表中提取 8 个核心指标的最新值、本周/本月均值和上期均值，
 *       计算环比变化百分比并标记趋势箭头</li>
 *   <li>将结构化数据组装成 prompt，通过 {@link ChatClient} 调用 AI（DeepSeek）
 *       生成包含整体评估、关键发现、风险提示、可执行建议的中文自然语言报告</li>
 * </ul>
 */
@Service
public class ReportService {

    /** 指标键名 → 中文标签映射，用于 AI prompt 和前端展示 */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("resting_heart_rate", "静息心率"), Map.entry("heart_rate_variability_sdnn", "HRV"),
            Map.entry("step_count", "步数"), Map.entry("active_energy_burned", "活动能量"),
            Map.entry("sleep_duration", "睡眠时长"), Map.entry("body_mass", "体重"),
            Map.entry("oxygen_saturation", "血氧"), Map.entry("workout", "运动时长"),
            Map.entry("heart_rate", "心率"), Map.entry("vo2max", "最大摄氧量"),
            Map.entry("respiratory_rate", "呼吸频率"), Map.entry("body_fat_percentage", "体脂率")
    );

    /** 指标键名 → 度量单位映射 */
    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("resting_heart_rate", "bpm"), Map.entry("heart_rate_variability_sdnn", "ms"),
            Map.entry("step_count", "步"), Map.entry("active_energy_burned", "kcal"),
            Map.entry("sleep_duration", "h"), Map.entry("body_mass", "kg"),
            Map.entry("oxygen_saturation", "%"), Map.entry("workout", "min"),
            Map.entry("heart_rate", "bpm"), Map.entry("vo2max", "mL"),
            Map.entry("respiratory_rate", "次/分"), Map.entry("body_fat_percentage", "%")
    );

    /**
     * 累积型指标集合 —— 这些指标在聚合时采用 SUM 汇总（如步数是每天累加的），
     * 查询时应使用 value_sum 而非 value_avg。
     */
    private static final Set<String> CUMULATIVE = Set.of(
            "step_count", "active_energy_burned", "basal_energy_burned", "flights_climbed",
            "distance_walking_running", "apple_exercise_time", "apple_stand_time",
            "workout", "headphone_audio_exposure", "time_in_daylight",
            "six_minute_walk_test_distance", "walking_running_distance",
            "sleep_duration"
    );

    private final JdbcTemplate jdbc;
    private final HealthStateClassifier classifier;
    private final ChatClient chatClient;

    /**
     * 构造注入所有依赖。
     *
     * @param jdbc       JDBC 模板，用于查询聚合数据
     * @param classifier 健康状态分类器，基于指标阈值判定每日健康等级
     * @param cb         Spring AI ChatClient 构建器，用于创建 AI 对话客户端
     */
    public ReportService(JdbcTemplate jdbc, HealthStateClassifier classifier, ChatClient.Builder cb) {
        this.jdbc = jdbc; this.classifier = classifier; this.chatClient = cb.build();
    }

    /**
     * 完整报告数据载体 —— 聚合了时间范围、状态分布、核心指标摘要和 AI 生成的叙述文本。
     *
     * @param type             报告类型："weekly"（周报）或 "monthly"（月报）
     * @param startDate        报告起始日期（含）
     * @param endDate          报告截止日期（含）
     * @param stateDistribution 每日健康状态分布，key 为状态名（如 "优"/"良"），value 为天数
     * @param highlights       核心指标摘要列表（最新值、均值、环比变化）
     * @param aiNarrative      AI 生成的自然语言报告正文
     */
    public record ReportData(
        String type, String startDate, String endDate,
        Map<String,Long> stateDistribution,
        List<MetricSummary> highlights,
        String aiNarrative
    ) {}

    /**
     * 单个指标的摘要信息 —— 用于报告中的关键指标卡片展示。
     *
     * @param metricKey  指标键名（如 "step_count"）
     * @param latest     最新一天的值
     * @param weeklyAvg  报告周期内的日均值（周报为 7 天，月报为 30 天）
     * @param priorAvg   上一周期的日均值（用于环比计算）
     * @param changePct  环比变化百分比 = (weeklyAvg - priorAvg) / priorAvg * 100
     * @param trend      趋势箭头符号：↑↑（大幅上升）/ ↑（小幅上升）/ →（持平）/ ↓ / ↓↓
     */
    public record MetricSummary(String metricKey, double latest, double weeklyAvg, double priorAvg, double changePct, String trend) {}

    /**
     * 生成完整的健康报告 —— 包含状态分布、核心指标摘要和 AI 叙述。
     *
     * <p>执行流程：
     * <ol>
     *   <li>确定报告时间范围：以数据最新日期为终点，向前取 7 天（周报）或 30 天（月报）</li>
     *   <li>调用 HealthStateClassifier 对周期内每天的健康状态进行分类，统计分布</li>
     *   <li>逐个查询 8 个核心指标的最新值、周期均值、上期均值，计算环比变化</li>
     *   <li>将结构化数据组装为 prompt 发送给 AI，生成自然语言报告</li>
     *   <li>清理 AI 返回的 markdown 格式标记，返回纯文本报告</li>
     * </ol>
     *
     * @param uploadId 上传记录的 UUID
     * @param type     报告类型："weekly" 或 "monthly"
     * @return 报告数据对象，若无数据则返回 null
     */
    public ReportData generateReport(String uploadId, String type) {
        // 查找该 upload 的数据截止日期（health_metric_daily 中的最大日期）
        String endDate = jdbc.queryForObject(
            "SELECT max(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId);
        if (endDate == null) return null;
        // 根据报告类型向前推算起始日期：周报取 7 天，月报取 30 天
        String startDate = type.equals("weekly")
            ? LocalDate.parse(endDate).minusDays(6).toString()
            : LocalDate.parse(endDate).minusDays(29).toString();

        // 利用分类器对报告周期内每天的健康状态进行分类，统计各状态出现天数
        var states = classifier.classify(uploadId, startDate, endDate);
        Map<String,Long> dist = new LinkedHashMap<>();
        for (var s : states) dist.merge(s.state(), 1L, Long::sum);

        // 查询 8 个核心指标的摘要数据：最新值、周期日均值、上期日均值、环比变化
        String[] keys = {"resting_heart_rate","heart_rate_variability_sdnn","step_count","active_energy_burned","sleep_duration","body_mass","oxygen_saturation","workout"};
        List<MetricSummary> highlights = new ArrayList<>();
        for (String mk : keys) {
            try {
                // 累积型指标用 value_sum，均值型指标用 value_avg
                String valCol = CUMULATIVE.contains(mk) ? "value_sum" : "value_avg";
                // 一次查询获取三个聚合值：最新值、本期均值（wk）、上期均值（prior）
                // "上期"定义为报告起始日前 7 天（startDate-7 到 startDate-1），与报告周期等长
                Map<String,Object> r = jdbc.queryForMap(
                    "SELECT coalesce((SELECT " + valCol + " FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key=? ORDER BY date DESC LIMIT 1),0) as latest, " +
                    "coalesce(avg(" + valCol + ") FILTER (WHERE date BETWEEN ?::date AND ?::date),0) as wk, " +
                    "coalesce(avg(" + valCol + ") FILTER (WHERE date BETWEEN ?::date AND ?::date),0) as prior " +
                    "FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key=?",
                    uploadId, mk, startDate, endDate, LocalDate.parse(startDate).minusDays(7).toString(), LocalDate.parse(startDate).minusDays(1).toString(), uploadId, mk);
                double latest = ((Number)r.get("latest")).doubleValue();
                double wk = ((Number)r.get("wk")).doubleValue();
                double prior = ((Number)r.get("prior")).doubleValue();
                // 环比变化百分比，避免除零（prior 为 0 时变化量记为 0）
                double chg = prior > 0 ? (wk - prior) / prior * 100 : 0;
                // 趋势阈值：>10% 为大幅变化（双箭头），>3% 为小幅变化（单箭头），其余为持平
                String trend = chg > 10 ? "↑↑" : chg > 3 ? "↑" : chg < -10 ? "↓↓" : chg < -3 ? "↓" : "→";
                highlights.add(new MetricSummary(mk, latest, wk, prior, chg, trend));
            } catch (Exception ignored) {
                // 某个指标可能在数据库中无数据，跳过不影响其他指标的计算
            }
        }

        // 将结构化数据组装为 AI prompt，调用 DeepSeek 生成中文健康报告
        String aiPrompt = buildReportPrompt(type, startDate, endDate, dist, highlights);
        String narrative = chatClient.prompt()
                .system("You are a sports medicine expert. Write a concise health report in Chinese. Include sections: 整体评估, 关键发现, 风险提示, 可执行建议. Be specific with numbers. Use section titles followed by content — NO markdown formatting, NO # symbols, NO **, just plain text with blank lines between sections.")
                .user(aiPrompt).call().content();

        // 清理 AI 返回中可能残留的 markdown 格式标记（代码块、标题符号、加粗符号）
        String clean = narrative != null ? narrative
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("(?m)^#{1,4}\\s+", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .trim() : "AI 分析暂时不可用";
        return new ReportData(type, startDate, endDate, dist, highlights, clean);
    }

    /**
     * 将报告的结构化数据组装为 AI prompt 文本。
     *
     * <p>prompt 包含三部分信息：
     * <ol>
     *   <li>报告类型和时间范围</li>
     *   <li>健康状态分布（如"优: 5天 良: 2天"）</li>
     *   <li>8 个核心指标的日均值、环比变化和趋势箭头</li>
     * </ol>
     *
     * @param type  报告类型："weekly" 或 "monthly"
     * @param start 起始日期字符串
     * @param end   结束日期字符串
     * @param dist  健康状态分布映射
     * @param hl    核心指标摘要列表
     * @return 格式化的 prompt 字符串，直接作为 AI 的 user message
     */
    private String buildReportPrompt(String type, String start, String end, Map<String,Long> dist, List<MetricSummary> hl) {
        StringBuilder sb = new StringBuilder();
        sb.append(type.equals("weekly") ? "周报" : "月报").append("，时间范围：").append(start).append(" 至 ").append(end).append("\n");
        // 拼接状态分布摘要，如 "优:5天 良:2天"
        sb.append("状态分布："); for (var e : dist.entrySet()) sb.append(e.getKey()).append(":").append(e.getValue()).append("天 ");
        sb.append("\n核心指标：\n");
        // 将每个指标格式化为 "标签: 日均XX单位 较前期+XX% ↑" 的形式
        for (var m : hl) {
            String label = LABELS.getOrDefault(m.metricKey(), m.metricKey());
            String unit = UNITS.getOrDefault(m.metricKey(), "");
            sb.append("- ").append(label).append(": 日均")
              .append(String.format("%.1f", m.weeklyAvg())).append(unit)
              .append(" 较前期").append(String.format("%+.0f%%", m.changePct())).append(" ").append(m.trend()).append("\n");
        }
        return sb.toString();
    }
}
