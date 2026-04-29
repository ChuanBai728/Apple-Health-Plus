package app.healthplus.api.service;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Rule-based daily physiological state classifier.
 * Categorizes each day into one of: 高压疲劳 / 高效恢复 / 运动爆发 / 平稳日常
 */
@Service
public class HealthStateClassifier {

    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','apple_exercise_time','apple_stand_time','workout'," +
            "'headphone_audio_exposure','time_in_daylight','walking_running_distance','sleep_duration'";

    private final JdbcTemplate jdbc;

    public HealthStateClassifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record DailyState(LocalDate date, String state, Map<String,Double> metrics) {}

    public List<DailyState> classify(String uploadId, String fromDate, String toDate) {
        List<DailyState> states = new ArrayList<>();
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT d.date, d.metric_key, " +
            "case when d.metric_key in (" + CUM_SET + ") then d.value_sum else d.value_avg end as val " +
            "FROM health_metric_daily d " +
            "WHERE d.upload_id=?::uuid AND d.date BETWEEN ?::date AND ?::date ORDER BY d.date",
            uploadId, fromDate, toDate);

        Map<LocalDate, Map<String,Double>> byDate = new LinkedHashMap<>();
        for (Map<String,Object> r : rows) {
            LocalDate date = ((java.sql.Date) r.get("date")).toLocalDate();
            String mk = (String) r.get("metric_key");
            Double v = r.get("val") != null ? ((Number) r.get("val")).doubleValue() : null;
            if (v != null) byDate.computeIfAbsent(date, k -> new HashMap<>()).put(mk, v);
        }

        // Compute baseline (30-day average) for key metrics
        Map<String,Double> baseline = new HashMap<>();
        for (String mk : List.of("resting_heart_rate","heart_rate_variability_sdnn","step_count","active_energy_burned","sleep_duration")) {
            double avg = byDate.values().stream().filter(m -> m.containsKey(mk))
                    .mapToDouble(m -> m.get(mk)).average().orElse(0);
            baseline.put(mk, avg);
        }

        for (var entry : byDate.entrySet()) {
            LocalDate d = entry.getKey();
            Map<String,Double> m = entry.getValue();
            String state = classifyDay(m, baseline);
            states.add(new DailyState(d, state, m));
        }
        return states;
    }

    private String classifyDay(Map<String,Double> m, Map<String,Double> baseline) {
        double rhr = m.getOrDefault("resting_heart_rate", baseline.get("resting_heart_rate"));
        double hrv = m.getOrDefault("heart_rate_variability_sdnn", baseline.get("heart_rate_variability_sdnn"));
        double steps = m.getOrDefault("step_count", 0.0);
        double energy = m.getOrDefault("active_energy_burned", 0.0);
        double sleep = m.getOrDefault("sleep_duration", 0.0);

        double blRhr = baseline.getOrDefault("resting_heart_rate", rhr);
        double blHrv = baseline.getOrDefault("heart_rate_variability_sdnn", hrv);

        boolean highStress = rhr > blRhr * 1.08 && hrv < blHrv * 0.85;
        boolean goodRecovery = rhr < blRhr * 0.95 && hrv > blHrv * 1.1;
        boolean highActivity = steps > 10000 || energy > 3.0;
        boolean lowSleep = sleep > 0 && sleep < 6.0;

        if (highStress && lowSleep) return "高压疲劳";
        if (goodRecovery && sleep >= 7) return "高效恢复";
        if (highActivity && !highStress) return "运动爆发";
        if (goodRecovery) return "高效恢复";
        if (highStress) return "高压疲劳";
        return "平稳日常";
    }

    public Map<String,Long> getStateDistribution(String uploadId) {
        List<DailyState> all = classify(uploadId,
            jdbc.queryForObject("SELECT min(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId),
            jdbc.queryForObject("SELECT max(date)::text FROM health_metric_daily WHERE upload_id=?::uuid", String.class, uploadId));
        Map<String,Long> dist = new LinkedHashMap<>();
        for (DailyState s : all) dist.merge(s.state(), 1L, Long::sum);
        return dist;
    }
}
