package app.healthplus.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import app.healthplus.domain.MetricGranularity;
import app.healthplus.domain.dto.MetricPoint;
import app.healthplus.domain.dto.MetricSeriesResponse;
import app.healthplus.domain.dto.OverviewCard;
import app.healthplus.domain.dto.OverviewResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 报告查询服务 —— 负责从 PostgreSQL 聚合表中读取健康指标数据，构建前端展示所需的响应。
 *
 * <p>设计思路：
 * <ul>
 *   <li>所有查询直接通过 {@link JdbcTemplate} 访问已由 aggregation-worker 预计算好的聚合表，
 *       避免在请求时做重复的实时计算，保证响应速度。</li>
 *   <li>指标分为「累积型」和「均值型」两类：累积型（步数、能量等）使用 value_sum 字段，
 *       均值型（心率、体重等）使用 value_avg 字段，通过 {@code CUM_SET} 集合统一判定。</li>
 *   <li>提供总览卡片（overview）和单指标时序（metric detail）两种查询入口，
 *       分别对应仪表盘首页和指标详情页。</li>
 * </ul>
 */
@Service
public class ReportQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 累积型指标的 metric_key 集合（SQL IN 子句片段）。
     * 这些指标（如步数、活动能量、睡眠时长等）在聚合时采用 SUM 汇总，
     * 其余指标（如心率、体重）则采用 AVG 取均值。
     * 使用字符串拼接方式嵌入 SQL，因为 JdbcTemplate 的参数化查询不支持 IN 列表动态展开。
     */
    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','distance_cycling','apple_exercise_time','apple_stand_time'," +
            "'workout','dietary_water','time_in_daylight','walking_running_distance','sleep_duration'";

    /**
     * 构建报告总览响应 —— 包含覆盖信息、所有指标的最新值/趋势/异常标记、最近 30 天时序数据和文本摘要。
     *
     * <p>执行流程：
     * <ol>
     *   <li>从 uploads 表获取数据覆盖范围（时间跨度、记录数、来源数）</li>
     *   <li>从 health_metric_daily 子查询获取每个指标最新一天的值、30 天基线偏差和异常标记</li>
     *   <li>一次查询拉取所有指标最近 30 天的数据点，按 metric_key 分组映射</li>
     *   <li>组装 OverviewCard 列表，附加文本摘要 headline</li>
     * </ol>
     *
     * @param reportId 上传记录的 UUID，对应 uploads 表主键
     * @return 总览响应对象，若 uploads 表中无此记录则返回含 unknown 状态的响应
     */
    public OverviewResponse getOverview(UUID reportId) {
        // 第一步：从 uploads 表获取数据覆盖元信息（状态、记录数、来源数、时间范围）
        Map<String, Object> upload = jdbcTemplate.queryForMap(
                "SELECT status, record_count, source_count, coverage_start_at, coverage_end_at FROM uploads WHERE id = ?::uuid",
                reportId
        );
        String status = (String) upload.get("status");
        long totalRecords = ((Number) upload.get("record_count")).longValue();
        int sourceCount = ((Number) upload.get("source_count")).intValue();
        Timestamp startTs = (Timestamp) upload.get("coverage_start_at");
        Timestamp endTs = (Timestamp) upload.get("coverage_end_at");
        // 将数据库 Timestamp 转为 Instant，供后续时间计算和前端展示使用
        Instant startDate = startTs != null ? startTs.toInstant() : null;
        Instant endDate = endTs != null ? endTs.toInstant() : null;

        // 第二步：获取每个指标最新一天的数据（最新值、30天趋势偏差、异常标记）
        // 通过关联子查询找到每个 metric_key 对应的最大日期，避免全表扫描
        // trend30d = 当天值 - baseline_avg_30d，反映偏离 30 天均值的程度
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT d.metric_key,
                       round(case when d.metric_key in (%s)
                              then d.value_sum else d.value_avg end::numeric, 1) as latest,
                       round((case when d.metric_key in (%s)
                              then d.value_sum else d.value_avg end - coalesce(d.baseline_avg_30d, case when d.metric_key in (%s)
                              then d.value_sum else d.value_avg end))::numeric, 1) as trend30d,
                       d.anomaly_flag,
                       d.date as latest_date
                FROM health_metric_daily d
                WHERE d.upload_id = ?::uuid
                  AND d.date = (
                      SELECT max(d2.date) FROM health_metric_daily d2
                      WHERE d2.upload_id = d.upload_id AND d2.metric_key = d.metric_key
                  )
                ORDER BY d.metric_key
                """.replace("%s", CUM_SET),
                reportId
        );

        // 第三步：一次查询拉取所有指标最近 30 天的数据点，避免 N+1 查询
        // 先查最大日期，再以该日期为基准向前取 30 天
        String recentDateStr = jdbcTemplate.queryForObject(
                "SELECT max(d2.date)::text FROM health_metric_daily d2 WHERE d2.upload_id = ?::uuid",
                String.class, reportId);
        Map<String, List<MetricPoint>> recentPointsMap = new HashMap<>();
        if (recentDateStr != null) {
            List<Map<String, Object>> recentRows = jdbcTemplate.queryForList(
                    """
                    SELECT metric_key, date::text as d,
                           round(case when metric_key in (%s) then value_sum else value_avg end::numeric, 1) as v
                    FROM health_metric_daily
                    """.replace("%s", CUM_SET) + """
                    WHERE upload_id = ?::uuid
                      AND date >= ?::date - interval '30 days'
                    ORDER BY metric_key, date
                    """,
                    reportId, recentDateStr
            );
            // 按 metric_key 分组，每个指标对应一个按日期排序的 MetricPoint 列表
            for (Map<String, Object> r : recentRows) {
                String mk = (String) r.get("metric_key");
                Double v = r.get("v") != null ? ((Number) r.get("v")).doubleValue() : null;
                String d = (String) r.get("d");
                recentPointsMap.computeIfAbsent(mk, k -> new ArrayList<>()).add(new MetricPoint(d, v));
            }
        }

        // 第四步：将原始行数据组装为 OverviewCard 列表，附带中文标签、单位和最近时序
        List<OverviewCard> cards = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("metric_key");
            Double latest = row.get("latest") != null ? ((Number) row.get("latest")).doubleValue() : null;
            Double trend30d = row.get("trend30d") != null ? ((Number) row.get("trend30d")).doubleValue() : null;
            boolean anomaly = row.get("anomaly_flag") != null && (Boolean) row.get("anomaly_flag");
            List<MetricPoint> recent = recentPointsMap.getOrDefault(key, List.of());
            cards.add(new OverviewCard(key, toLabelStr(key), latest, toUnitStr(key), trend30d, anomaly, recent));
        }

        // 组装覆盖范围摘要（总记录数、指标数、来源数、时间跨度）
        OverviewResponse.Coverage coverage = new OverviewResponse.Coverage(
                totalRecords, cards.size(), sourceCount, startDate, endDate
        );

        // 根据趋势和异常情况生成一句话文本摘要
        String headline = buildHeadline(cards, startDate, endDate);

        return new OverviewResponse(reportId, status != null ? status : "unknown", coverage, cards, headline);
    }

    /**
     * 根据指标卡片列表生成一句话健康摘要（headline）。
     *
     * <p>策略优先级：异常指标数 > 下降指标数 > 默认正面评价。
     * 将数据覆盖时间范围和健康状态判断拼接为一段自然语言文本，供前端首页直接展示。
     *
     * @param cards 所有指标的概览卡片列表
     * @param start 数据覆盖起始时间（可为 null）
     * @param end   数据覆盖结束时间（可为 null）
     * @return 中文摘要字符串
     */
    private String buildHeadline(List<OverviewCard> cards, Instant start, Instant end) {
        if (cards.isEmpty()) return "暂无足够数据生成健康摘要。";

        // 统计上升/下降/异常的指标数量，用于后续判断整体健康趋势
        int upCount = 0, downCount = 0, anomalyCount = 0;
        for (OverviewCard c : cards) {
            if (c.anomaly()) anomalyCount++;
            if (c.trend30d() != null && c.trend30d() > 0) upCount++;
            else if (c.trend30d() != null && c.trend30d() < 0) downCount++;
        }

        StringBuilder sb = new StringBuilder();
        // 拼接数据覆盖时间段描述，优先显示月数，不足一个月则显示天数
        if (start != null && end != null) {
            // 使用 Asia/Shanghai 时区转换，确保日期边界与用户所在时区一致
            LocalDate s = start.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            LocalDate e = end.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            long months = ChronoUnit.MONTHS.between(s, e);
            long days = ChronoUnit.DAYS.between(s, e);
            sb.append("数据覆盖 ").append(s).append(" 至 ").append(e);
            if (months > 0) sb.append("，跨度约 ").append(months).append(" 个月。");
            else sb.append("，共 ").append(days).append(" 天。");
        }

        // 异常优先提示，其次关注下降趋势，最后给出正面评价
        if (anomalyCount > 0) {
            sb.append("检测到 ").append(anomalyCount).append(" 项指标存在异常波动，建议重点关注。");
        } else if (downCount > upCount) {
            sb.append("部分指标近期有所下降，建议关注恢复状态。");
        } else {
            sb.append("多数指标保持稳定，整体状态良好。");
        }

        return sb.toString();
    }

    /**
     * 查询单个指标的时序数据（委托给双指标重载方法，第二个指标传 null）。
     *
     * @param reportId   上传记录 UUID
     * @param metricKey  指标键名（如 "step_count"）
     * @param granularity 时间粒度：DAILY / WEEKLY / MONTHLY
     * @return 指标时序响应
     */
    public MetricSeriesResponse getMetric(UUID reportId, String metricKey, MetricGranularity granularity) {
        return getMetric(reportId, metricKey, null, granularity);
    }

    /**
     * 查询一个或两个指标的时序数据，支持单指标详情和双指标对比两种模式。
     *
     * <p>设计要点：
     * <ul>
     *   <li>根据 granularity 动态选择聚合表（daily/weekly/monthly）和日期列名，复用同一套查询逻辑</li>
     *   <li>双指标模式下，两个指标的数据点合并到同一个列表中返回，
     *       前端可通过 metricKey 字段区分绘制两条折线</li>
     *   <li>单指标模式下，额外查询 min/max/baseline/trend/anomaly 等统计字段，
     *       并将异常上下文附加到最后一个数据点上，便于前端在折线图中标注异常</li>
     * </ul>
     *
     * @param reportId    上传记录 UUID
     * @param metricKey   主指标键名
     * @param metricKey2  对比指标键名（可为 null，表示单指标模式）
     * @param granularity 时间粒度：DAILY / WEEKLY / MONTHLY
     * @return 指标时序响应，包含数据点列表
     */
    public MetricSeriesResponse getMetric(UUID reportId, String metricKey, String metricKey2, MetricGranularity granularity) {
        // 根据粒度映射到对应的聚合表和日期列，避免为每个粒度写重复的查询方法
        String table = switch (granularity) {
            case DAILY -> "health_metric_daily";
            case WEEKLY -> "health_metric_weekly";
            case MONTHLY -> "health_metric_monthly";
        };
        String dateColumn = switch (granularity) {
            case DAILY -> "date";
            case WEEKLY -> "week_start";
            case MONTHLY -> "month_start";
        };

        if (metricKey2 != null) {
            // 双指标对比模式：根据第一个指标是否为累积型选择 value_sum 或 value_avg
            // 注意：这里简化处理，仅以 metricKey 判定列类型，metricKey2 可能类型不同
            String valCol = CUM_SET.contains("'" + metricKey + "'") ? "value_sum" : "value_avg";
            String sql = "SELECT " + dateColumn + " as d, metric_key, " + valCol + " as value_avg " +
                    "FROM " + table + " WHERE upload_id = ?::uuid AND metric_key IN (?, ?) ORDER BY d, metric_key";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, reportId, metricKey, metricKey2);

            // 使用 LinkedHashMap 保证插入顺序，先主指标后对比指标
            Map<String, List<MetricPoint>> byMetric = new LinkedHashMap<>();
            byMetric.put(metricKey, new ArrayList<>());
            byMetric.put(metricKey2, new ArrayList<>());
            for (Map<String, Object> row : rows) {
                String mk = (String) row.get("metric_key");
                Double v = row.get("value_avg") != null ? ((Number) row.get("value_avg")).doubleValue() : null;
                String d = row.get("d") != null ? row.get("d").toString() : "";
                List<MetricPoint> list = byMetric.computeIfAbsent(mk, k -> new ArrayList<>());
                list.add(new MetricPoint(d, v));
            }
            // 将两个指标的数据点顺序拼接，前端用 metricKey 字段区分属于哪条折线
            List<MetricPoint> combined = new ArrayList<>();
            combined.addAll(byMetric.getOrDefault(metricKey, List.of()));
            combined.addAll(byMetric.getOrDefault(metricKey2, List.of()));
            return new MetricSeriesResponse(
                    metricKey + "," + metricKey2,
                    toLabelStr(metricKey) + " vs " + toLabelStr(metricKey2),
                    granularity.name().toLowerCase(),
                    combined
            );
        }

        // 单指标详情模式：查询完整的时间序列 + 统计字段
        // 仅 daily 表有 anomaly_flag 字段，其他粒度表不包含，通过字符串拼接动态控制 SELECT 列
        // "1 as dummy" 是占位符，确保 SQL 在有/无 anomaly_flag 时语法都正确
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + dateColumn + " as d, " +
                (CUM_SET.contains("'" + metricKey + "'") ? "value_sum" : "value_avg") + " as value_avg, " +
                "value_min, value_max, baseline_avg_30d, trend_delta_7d, trend_delta_30d, " +
                (table.equals("health_metric_daily") ? "anomaly_flag, " : "") +
                "1 as dummy " +
                "FROM " + table + " WHERE upload_id = ?::uuid AND metric_key = ? ORDER BY d",
                reportId, metricKey
        );

        List<MetricPoint> points = new ArrayList<>();
        boolean anomaly = false;
        Double baseline = null, delta7 = null, delta30 = null;
        for (Map<String, Object> row : rows) {
            Double value = row.get("value_avg") != null ? ((Number) row.get("value_avg")).doubleValue() : null;
            String date = row.get("d") != null ? row.get("d").toString() : "";
            points.add(new MetricPoint(date, value));
            // 遍历过程中持续更新统计字段，最终保留的是最后一天的值（即最新值）
            if (row.get("anomaly_flag") instanceof Boolean f && f) anomaly = true;
            if (row.get("baseline_avg_30d") != null) baseline = ((Number) row.get("baseline_avg_30d")).doubleValue();
            if (row.get("trend_delta_7d") != null) delta7 = ((Number) row.get("trend_delta_7d")).doubleValue();
            if (row.get("trend_delta_30d") != null) delta30 = ((Number) row.get("trend_delta_30d")).doubleValue();
        }

        // 将异常上下文（baseline、趋势偏差、异常标记）附加到最后一个数据点
        // 前端可据此在折线图的最后一个点上渲染异常标注
        if (!points.isEmpty() && anomaly) {
            MetricPoint last = points.remove(points.size() - 1);
            points.add(new MetricPoint(last.date(), last.value(), baseline, delta7, delta30, anomaly));
        }

        return new MetricSeriesResponse(metricKey, toLabelStr(metricKey), granularity.name().toLowerCase(), points);
    }

    /**
     * 将数据库中的 metric_key 映射为中文显示名称，用于前端卡片和图表的标题展示。
     * 未匹配的 key 原样返回，避免空指针。
     *
     * @param metricKey 数据库中的指标键名（如 "step_count"）
     * @return 中文标签（如 "步数"），未知 key 返回原始字符串
     */
    private String toLabelStr(String metricKey) {
        return switch (metricKey) {
            case "sleep_duration" -> "睡眠时长";
            case "resting_heart_rate" -> "静息心率";
            case "heart_rate_variability", "heart_rate_variability_sdnn" -> "心率变异性 (HRV)";
            case "heart_rate" -> "心率";
            case "step_count" -> "步数";
            case "active_energy_burned" -> "活动能量";
            case "walking_running_distance", "distance_walking_running" -> "步行/跑步距离";
            case "body_mass" -> "体重";
            case "body_fat_percentage" -> "体脂率";
            case "oxygen_saturation" -> "血氧饱和度";
            case "respiratory_rate" -> "呼吸频率";
            case "vo2max" -> "最大摄氧量";
            case "workout" -> "训练时长";
            case "flights_climbed" -> "爬楼";
            case "basal_energy_burned" -> "基础代谢";
            case "body_mass_index" -> "BMI";
            case "apple_exercise_time" -> "锻炼时长";
            case "apple_stand_time" -> "站立时长";
            case "walking_speed" -> "步行速度";
            case "walking_step_length" -> "步长";
            case "walking_heart_rate_average" -> "步行心率";
            case "walking_double_support_percentage" -> "双支撑占比";
            case "walking_asymmetry_percentage" -> "步态不对称";
            case "apple_walking_steadiness" -> "步行稳定性";
            case "apple_sleeping_wrist_temperature" -> "睡眠腕温";
            case "environmental_audio_exposure" -> "环境噪音";
            case "headphone_audio_exposure" -> "耳机音量";
            case "time_in_daylight" -> "日照时长";
            case "dietary_water" -> "饮水量";
            case "physical_effort" -> "体力负荷";
            case "waist_circumference" -> "腰围";
            case "lean_body_mass" -> "去脂体重";
            case "height" -> "身高";
            default -> metricKey;
        };
    }

    /**
     * 将数据库中的 metric_key 映射为对应的度量单位字符串，用于前端数值后缀展示。
     * 未匹配的 key 返回空字符串。
     *
     * @param metricKey 数据库中的指标键名
     * @return 单位字符串（如 "bpm"、"步"、"kcal"），未知 key 返回空串
     */
    private String toUnitStr(String metricKey) {
        return switch (metricKey) {
            case "sleep_duration" -> "h";
            case "resting_heart_rate", "heart_rate", "heart_rate_recovery_one_minute", "walking_heart_rate_average" -> "bpm";
            case "heart_rate_variability", "heart_rate_variability_sdnn" -> "ms";
            case "step_count" -> "步";
            case "active_energy_burned", "basal_energy_burned" -> "kcal";
            case "distance_walking_running", "walking_running_distance" -> "km";
            case "body_mass" -> "kg";
            case "body_fat_percentage", "oxygen_saturation", "walking_asymmetry_percentage", "walking_double_support_percentage" -> "%";
            case "respiratory_rate" -> "次/分";
            case "workout", "apple_exercise_time", "apple_stand_time", "time_in_daylight" -> "min";
            default -> "";
        };
    }
}
