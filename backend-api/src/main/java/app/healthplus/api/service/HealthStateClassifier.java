package app.healthplus.api.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于规则的每日生理状态分类器。
 *
 * <h3>职责</h3>
 * <p>根据用户每日的健康指标（心率、HRV、步数、能量消耗、睡眠时长等），
 * 将每天归入以下四种状态之一：</p>
 * <ul>
 *   <li><b>高压疲劳</b> —— 静息心率偏高 + HRV 偏低 + 睡眠不足，身体处于应激状态</li>
 *   <li><b>高效恢复</b> —— 静息心率偏低 + HRV 偏高 + 睡眠充足，身体恢复良好</li>
 *   <li><b>运动爆发</b> —— 高步数或高能量消耗，当天有较强运动</li>
 *   <li><b>平稳日常</b> —— 无明显异常，正常的一天</li>
 * </ul>
 *
 * <h3>设计思路</h3>
 * <p>采用「基线对比」策略：先计算整个查询周期内关键指标的平均值作为个人基线，
 * 再将每天的指标与基线比较，通过阈值判定当天状态。
 * 基线使用查询范围内的数据计算（而非固定的 30 天），以适应不同用户的数据分布。</p>
 * <p>分类优先级：高压疲劳 + 睡眠不足 > 高效恢复 + 充足睡眠 > 运动爆发 > 高效恢复 > 高压疲劳 > 平稳日常</p>
 */
@Service
public class HealthStateClassifier {

    /**
     * 累计型指标集合 —— 这些指标在聚合表中使用 value_sum（日总和）而非 value_avg（日均值）。
     * 例如：步数、能量消耗是全天累计值，取总和才有意义；
     * 而心率、HRV 是瞬时/均值型指标，取均值更合理。
     */
    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','apple_exercise_time','apple_stand_time','workout'," +
            "'headphone_audio_exposure','time_in_daylight','walking_running_distance','sleep_duration'";

    private final JdbcTemplate jdbc;

    public HealthStateClassifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 单日状态记录 —— 包含日期、判定状态和当日所有指标值。
     *
     * @param date    日期
     * @param state   分类结果（高压疲劳 / 高效恢复 / 运动爆发 / 平稳日常）
     * @param metrics 当日所有健康指标的 key-value 映射
     */
    public record DailyState(LocalDate date, String state, Map<String,Double> metrics) {}

    /**
     * 对指定上传记录在给定日期范围内的每一天进行健康状态分类。
     *
     * @param uploadId 上传记录的 UUID（字符串形式）
     * @param fromDate 起始日期（yyyy-MM-dd 格式）
     * @param toDate   结束日期（yyyy-MM-dd 格式）
     * @return 按日期排序的每日状态列表
     */
    public List<DailyState> classify(String uploadId, String fromDate, String toDate) {
        List<DailyState> states = new ArrayList<>();

        // 从日聚合表查询指标数据：
        // - 累计型指标（如步数）取 value_sum（当日总和）
        // - 均值型指标（如心率）取 value_avg（当日均值）
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT d.date, d.metric_key, " +
            "case when d.metric_key in (" + CUM_SET + ") then d.value_sum else d.value_avg end as val " +
            "FROM health_metric_daily d " +
            "WHERE d.upload_id=?::uuid AND d.date BETWEEN ?::date AND ?::date ORDER BY d.date",
            uploadId, fromDate, toDate);

        // 将扁平的行数据按日期分组，转换为 Map<日期, Map<指标名, 指标值>>
        Map<LocalDate, Map<String,Double>> byDate = new LinkedHashMap<>();
        for (Map<String,Object> r : rows) {
            LocalDate date = ((java.sql.Date) r.get("date")).toLocalDate();
            String mk = (String) r.get("metric_key");
            Double v = r.get("val") != null ? ((Number) r.get("val")).doubleValue() : null;
            if (v != null) byDate.computeIfAbsent(date, k -> new HashMap<>()).put(mk, v);
        }

        // 计算关键指标的基线值（查询范围内的平均值）
        // 基线用于判断某天的指标是偏高还是偏低，是分类的核心参照物
        Map<String,Double> baseline = new HashMap<>();
        for (String mk : List.of("resting_heart_rate","heart_rate_variability_sdnn","step_count","active_energy_burned","sleep_duration")) {
            double avg = byDate.values().stream().filter(m -> m.containsKey(mk))
                    .mapToDouble(m -> m.get(mk)).average().orElse(0);
            baseline.put(mk, avg);
        }

        // 逐日分类
        for (var entry : byDate.entrySet()) {
            LocalDate d = entry.getKey();
            Map<String,Double> m = entry.getValue();
            String state = classifyDay(m, baseline);
            states.add(new DailyState(d, state, m));
        }
        return states;
    }

    /**
     * 对单日进行状态分类 —— 核心决策逻辑。
     * <p>通过对比当日指标与基线值，使用阈值判定四个布尔条件，
     * 再按优先级组合得出最终状态。</p>
     *
     * @param m        当日指标映射
     * @param baseline 基线指标映射（查询范围内的平均值）
     * @return 分类结果字符串
     */
    private String classifyDay(Map<String,Double> m, Map<String,Double> baseline) {
        // 优先使用当天的实际数据，缺失时回退到基线值
        double rhr = m.getOrDefault("resting_heart_rate", baseline.get("resting_heart_rate"));
        double hrv = m.getOrDefault("heart_rate_variability_sdnn", baseline.get("heart_rate_variability_sdnn"));
        double steps = m.getOrDefault("step_count", 0.0);
        double energy = m.getOrDefault("active_energy_burned", 0.0);
        double sleep = m.getOrDefault("sleep_duration", 0.0);

        double blRhr = baseline.getOrDefault("resting_heart_rate", rhr);
        double blHrv = baseline.getOrDefault("heart_rate_variability_sdnn", hrv);

        // 高压判定：静息心率高于基线 8% 且 HRV 低于基线 15%，表示交感神经兴奋、身体应激
        boolean highStress = rhr > blRhr * 1.08 && hrv < blHrv * 0.85;
        // 良好恢复判定：静息心率低于基线 5% 且 HRV 高于基线 10%，表示副交感神经占优、恢复良好
        boolean goodRecovery = rhr < blRhr * 0.95 && hrv > blHrv * 1.1;
        // 高活动量判定：步数超 1 万或活跃能量消耗超 3 千卡
        boolean highActivity = steps > 10000 || energy > 3.0;
        // 睡眠不足判定：有睡眠数据但不足 6 小时
        boolean lowSleep = sleep > 0 && sleep < 6.0;

        // 按优先级匹配：最严重的状态优先返回
        if (highStress && lowSleep) return "高压疲劳";    // 高压 + 缺觉 = 最差状态
        if (goodRecovery && sleep >= 7) return "高效恢复"; // 恢复好 + 睡够 = 最佳状态
        if (highActivity && !highStress) return "运动爆发"; // 运动量大但身体无应激 = 运动日
        if (goodRecovery) return "高效恢复";               // 仅恢复好但睡眠未达 7h
        if (highStress) return "高压疲劳";                 // 仅高压但睡眠尚可
        return "平稳日常";                                 // 无明显特征 = 正常日
    }

    /**
     * 获取上传记录中所有日期的状态分布统计。
     * <p>自动查询该上传记录的最小/最大日期，然后调用 {@link #classify} 进行全量分类，
     * 最后按状态名聚合计数。</p>
     *
     * @param uploadId 上传记录的 UUID（字符串形式）
     * @return 状态名称 -> 出现天数的映射，保持插入顺序
     */
    public Map<String,Long> getStateDistribution(String uploadId) {
        // 自动获取该上传记录的日期范围，无需外部传入
        List<DailyState> all = classify(uploadId,
            jdbc.queryForObject("SELECT min(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId),
            jdbc.queryForObject("SELECT max(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId));
        // 按状态名累加计数
        Map<String,Long> dist = new LinkedHashMap<>();
        for (DailyState s : all) dist.merge(s.state(), 1L, Long::sum);
        return dist;
    }
}
