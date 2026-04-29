package app.healthplus.api.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("resting_heart_rate", "静息心率"), Map.entry("heart_rate_variability_sdnn", "HRV"),
            Map.entry("step_count", "步数"), Map.entry("active_energy_burned", "活动能量"),
            Map.entry("sleep_duration", "睡眠时长"), Map.entry("body_mass", "体重"),
            Map.entry("oxygen_saturation", "血氧"), Map.entry("workout", "运动时长"),
            Map.entry("heart_rate", "心率"), Map.entry("vo2max", "最大摄氧量"),
            Map.entry("respiratory_rate", "呼吸频率"), Map.entry("body_fat_percentage", "体脂率")
    );

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("resting_heart_rate", "bpm"), Map.entry("heart_rate_variability_sdnn", "ms"),
            Map.entry("step_count", "步"), Map.entry("active_energy_burned", "kcal"),
            Map.entry("sleep_duration", "h"), Map.entry("body_mass", "kg"),
            Map.entry("oxygen_saturation", "%"), Map.entry("workout", "min"),
            Map.entry("heart_rate", "bpm"), Map.entry("vo2max", "mL"),
            Map.entry("respiratory_rate", "次/分"), Map.entry("body_fat_percentage", "%")
    );

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

    public ReportService(JdbcTemplate jdbc, HealthStateClassifier classifier, ChatClient.Builder cb) {
        this.jdbc = jdbc; this.classifier = classifier; this.chatClient = cb.build();
    }

    public record ReportData(
        String type, String startDate, String endDate,
        Map<String,Long> stateDistribution,
        List<MetricSummary> highlights,
        String aiNarrative
    ) {}

    public record MetricSummary(String metricKey, double latest, double weeklyAvg, double priorAvg, double changePct, String trend) {}

    public ReportData generateReport(String uploadId, String type) {
        String endDate = jdbc.queryForObject(
            "SELECT max(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId);
        if (endDate == null) return null;
        String startDate = type.equals("weekly")
            ? LocalDate.parse(endDate).minusDays(6).toString()
            : LocalDate.parse(endDate).minusDays(29).toString();

        // State distribution
        var states = classifier.classify(uploadId, startDate, endDate);
        Map<String,Long> dist = new LinkedHashMap<>();
        for (var s : states) dist.merge(s.state(), 1L, Long::sum);

        // Highlight metrics: key indicators with trends
        String[] keys = {"resting_heart_rate","heart_rate_variability_sdnn","step_count","active_energy_burned","sleep_duration","body_mass","oxygen_saturation","workout"};
        List<MetricSummary> highlights = new ArrayList<>();
        for (String mk : keys) {
            try {
                String valCol = CUMULATIVE.contains(mk) ? "value_sum" : "value_avg";
                Map<String,Object> r = jdbc.queryForMap(
                    "SELECT coalesce((SELECT " + valCol + " FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key=? ORDER BY date DESC LIMIT 1),0) as latest, " +
                    "coalesce(avg(" + valCol + ") FILTER (WHERE date BETWEEN ?::date AND ?::date),0) as wk, " +
                    "coalesce(avg(" + valCol + ") FILTER (WHERE date BETWEEN ?::date AND ?::date),0) as prior " +
                    "FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key=?",
                    uploadId, mk, startDate, endDate, LocalDate.parse(startDate).minusDays(7).toString(), LocalDate.parse(startDate).minusDays(1).toString(), uploadId, mk);
                double latest = ((Number)r.get("latest")).doubleValue();
                double wk = ((Number)r.get("wk")).doubleValue();
                double prior = ((Number)r.get("prior")).doubleValue();
                double chg = prior > 0 ? (wk - prior) / prior * 100 : 0;
                String trend = chg > 10 ? "↑↑" : chg > 3 ? "↑" : chg < -10 ? "↓↓" : chg < -3 ? "↓" : "→";
                highlights.add(new MetricSummary(mk, latest, wk, prior, chg, trend));
            } catch (Exception ignored) {}
        }

        // AI narrative
        String aiPrompt = buildReportPrompt(type, startDate, endDate, dist, highlights);
        String narrative = chatClient.prompt()
                .system("You are a sports medicine expert. Write a concise health report in Chinese. Include sections: 整体评估, 关键发现, 风险提示, 可执行建议. Be specific with numbers. Use section titles followed by content — NO markdown formatting, NO # symbols, NO **, just plain text with blank lines between sections.")
                .user(aiPrompt).call().content();

        String clean = narrative != null ? narrative
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("(?m)^#{1,4}\\s+", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .trim() : "AI 分析暂时不可用";
        return new ReportData(type, startDate, endDate, dist, highlights, clean);
    }

    private String buildReportPrompt(String type, String start, String end, Map<String,Long> dist, List<MetricSummary> hl) {
        StringBuilder sb = new StringBuilder();
        sb.append(type.equals("weekly") ? "周报" : "月报").append("，时间范围：").append(start).append(" 至 ").append(end).append("\n");
        sb.append("状态分布："); for (var e : dist.entrySet()) sb.append(e.getKey()).append(":").append(e.getValue()).append("天 ");
        sb.append("\n核心指标：\n");
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
