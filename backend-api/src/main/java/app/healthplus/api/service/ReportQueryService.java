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

@Service
public class ReportQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','distance_cycling','apple_exercise_time','apple_stand_time'," +
            "'workout','dietary_water','time_in_daylight','walking_running_distance','sleep_duration'";

    public OverviewResponse getOverview(UUID reportId) {
        // Get coverage info
        Map<String, Object> upload = jdbcTemplate.queryForMap(
                "SELECT status, record_count, source_count, coverage_start_at, coverage_end_at FROM uploads WHERE id = ?::uuid",
                reportId
        );
        String status = (String) upload.get("status");
        long totalRecords = ((Number) upload.get("record_count")).longValue();
        int sourceCount = ((Number) upload.get("source_count")).intValue();
        Timestamp startTs = (Timestamp) upload.get("coverage_start_at");
        Timestamp endTs = (Timestamp) upload.get("coverage_end_at");
        Instant startDate = startTs != null ? startTs.toInstant() : null;
        Instant endDate = endTs != null ? endTs.toInstant() : null;

        // Get latest value + trend + anomaly for each metric
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

        // Get recent 30 points per metric in one query
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
            for (Map<String, Object> r : recentRows) {
                String mk = (String) r.get("metric_key");
                Double v = r.get("v") != null ? ((Number) r.get("v")).doubleValue() : null;
                String d = (String) r.get("d");
                recentPointsMap.computeIfAbsent(mk, k -> new ArrayList<>()).add(new MetricPoint(d, v));
            }
        }

        List<OverviewCard> cards = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("metric_key");
            Double latest = row.get("latest") != null ? ((Number) row.get("latest")).doubleValue() : null;
            Double trend30d = row.get("trend30d") != null ? ((Number) row.get("trend30d")).doubleValue() : null;
            boolean anomaly = row.get("anomaly_flag") != null && (Boolean) row.get("anomaly_flag");
            List<MetricPoint> recent = recentPointsMap.getOrDefault(key, List.of());
            cards.add(new OverviewCard(key, toLabelStr(key), latest, toUnitStr(key), trend30d, anomaly, recent));
        }

        OverviewResponse.Coverage coverage = new OverviewResponse.Coverage(
                totalRecords, cards.size(), sourceCount, startDate, endDate
        );

        String headline = buildHeadline(cards, startDate, endDate);

        return new OverviewResponse(reportId, status != null ? status : "unknown", coverage, cards, headline);
    }

    private String buildHeadline(List<OverviewCard> cards, Instant start, Instant end) {
        if (cards.isEmpty()) return "暂无足够数据生成健康摘要。";

        int upCount = 0, downCount = 0, anomalyCount = 0;
        for (OverviewCard c : cards) {
            if (c.anomaly()) anomalyCount++;
            if (c.trend30d() != null && c.trend30d() > 0) upCount++;
            else if (c.trend30d() != null && c.trend30d() < 0) downCount++;
        }

        StringBuilder sb = new StringBuilder();
        if (start != null && end != null) {
            LocalDate s = start.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            LocalDate e = end.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            long months = ChronoUnit.MONTHS.between(s, e);
            long days = ChronoUnit.DAYS.between(s, e);
            sb.append("数据覆盖 ").append(s).append(" 至 ").append(e);
            if (months > 0) sb.append("，跨度约 ").append(months).append(" 个月。");
            else sb.append("，共 ").append(days).append(" 天。");
        }

        if (anomalyCount > 0) {
            sb.append("检测到 ").append(anomalyCount).append(" 项指标存在异常波动，建议重点关注。");
        } else if (downCount > upCount) {
            sb.append("部分指标近期有所下降，建议关注恢复状态。");
        } else {
            sb.append("多数指标保持稳定，整体状态良好。");
        }

        return sb.toString();
    }

    public MetricSeriesResponse getMetric(UUID reportId, String metricKey, MetricGranularity granularity) {
        return getMetric(reportId, metricKey, null, granularity);
    }

    public MetricSeriesResponse getMetric(UUID reportId, String metricKey, String metricKey2, MetricGranularity granularity) {
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
            // Dual metric query — use value_sum for cumulative metrics
            String valCol = CUM_SET.contains("'" + metricKey + "'") ? "value_sum" : "value_avg";
            String sql = "SELECT " + dateColumn + " as d, metric_key, " + valCol + " as value_avg " +
                    "FROM " + table + " WHERE upload_id = ?::uuid AND metric_key IN (?, ?) ORDER BY d, metric_key";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, reportId, metricKey, metricKey2);

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
            // Return combined response with metricKey as identifier
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
            if (row.get("anomaly_flag") instanceof Boolean f && f) anomaly = true;
            if (row.get("baseline_avg_30d") != null) baseline = ((Number) row.get("baseline_avg_30d")).doubleValue();
            if (row.get("trend_delta_7d") != null) delta7 = ((Number) row.get("trend_delta_7d")).doubleValue();
            if (row.get("trend_delta_30d") != null) delta30 = ((Number) row.get("trend_delta_30d")).doubleValue();
        }

        return new MetricSeriesResponse(metricKey, toLabelStr(metricKey), granularity.name().toLowerCase(),
                points, anomaly, baseline, delta7, delta30);
    }

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
