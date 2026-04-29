package app.healthplus.aggregation.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import app.healthplus.ai.AiAnswer;
import app.healthplus.ai.AiIntent;
import app.healthplus.ai.HealthKnowledgeBase;
import java.time.Instant;
import java.util.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiOrchestratorService {

    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','apple_exercise_time','apple_stand_time','workout'," +
            "'headphone_audio_exposure','time_in_daylight','walking_running_distance','sleep_duration'";

    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final HealthKnowledgeBase knowledgeBase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiOrchestratorService(JdbcTemplate jdbcTemplate, ChatClient.Builder builder,
                                  HealthKnowledgeBase knowledgeBase) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = builder.build();
        this.knowledgeBase = knowledgeBase;
    }

    public AiAnswer analyzeUpload(String uploadId, String question) {
        String ctx = buildInsightContext(uploadId);
        String ragKnowledge = HealthKnowledgeBase.formatContext(
                knowledgeBase.retrieve(question, null, 3));

        String systemPrompt = """
            You are a sports medicine expert and personal wellness coach.
            Use the provided professional health knowledge as your reference.

            Professional Health Knowledge:
            %s

            Analyze cross-dimensionally: sleep↔HRV, activity↔RHR, noise↔SpO2, weight↔VO2max.
            Reference normal ranges from the knowledge base. Be warm but professional.
            Respond in JSON:
            {"intent":"...","conclusion":"...","evidence":[...],"advice":[...],"disclaimer":"..."}"""
            .formatted(ragKnowledge);

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(ctx + "\n\nUser question: " + (question != null ? question : "Analyze my overall health"))
                .call().content();

        return parseResponse(response, AiIntent.OVERALL_SUMMARY);
    }

    private String buildInsightContext(String uploadId) {
        if (uploadId == null) return "No data.";
        StringBuilder c = new StringBuilder("=== Health Data Insights ===\n");

        // Basic stats
        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
            "SELECT metric_key, round(avg(case when metric_key in (" + CUM_SET + ") then value_sum else value_avg end)::numeric,2) as m, " +
            "min(value_min) as lo, max(value_max) as hi, count(*) as n, " +
            "(SELECT case when metric_key in (" + CUM_SET + ") then value_sum else value_avg end FROM health_metric_daily d2 WHERE d2.upload_id=d.upload_id AND d2.metric_key=d.metric_key ORDER BY date DESC LIMIT 1) as latest " +
            "FROM health_metric_daily d WHERE upload_id=?::uuid AND value_avg IS NOT NULL GROUP BY metric_key ORDER BY n DESC", uploadId);
        for (Map<String,Object> r: stats) c.append(r.get("metric_key")).append(": avg=").append(r.get("m"))
            .append(" range=").append(r.get("lo")).append("-").append(r.get("hi"))
            .append(" days=").append(r.get("n")).append(" latest=").append(r.get("latest")).append("\n");

        // Sleep ↔ Recovery correlation
        c.append("\n=== Sleep-Recovery Correlation ===\n");
        List<Map<String,Object>> sc = jdbcTemplate.queryForList(
            "SELECT sl.value_avg as sleep, COALESCE(hr.value_avg,0) as hrv, COALESCE(rh.value_avg,0) as rhr FROM health_metric_daily sl " +
            "LEFT JOIN health_metric_daily hr ON hr.upload_id=sl.upload_id AND hr.metric_key='heart_rate_variability_sdnn' AND hr.date=sl.date " +
            "LEFT JOIN health_metric_daily rh ON rh.upload_id=sl.upload_id AND rh.metric_key='resting_heart_rate' AND rh.date=sl.date " +
            "WHERE sl.upload_id=?::uuid AND sl.metric_key='sleep_duration' ORDER BY sl.date DESC LIMIT 30", uploadId);
        double gH=0,gR=0,bH=0,bR=0; int g=0,b=0;
        for (Map<String,Object> r: sc) {
            double s=((Number)r.get("sleep")).doubleValue(), hv=((Number)r.get("hrv")).doubleValue(), rh=((Number)r.get("rhr")).doubleValue();
            if(s>=6.5){gH+=hv;gR+=rh;g++;}else{bH+=hv;bR+=rh;b++;}
        }
        if(g>0&&b>0){gH/=g;gR/=g;bH/=b;bR/=b;
            c.append("Sleep≥6.5h: HRV=").append(String.format("%.0f",gH)).append("ms RHR=").append(String.format("%.0f",gR)).append("bpm (").append(g).append("d)\n");
            c.append("Sleep<6.5h: HRV=").append(String.format("%.0f",bH)).append("ms RHR=").append(String.format("%.0f",bR)).append("bpm (").append(b).append("d)\n");
            double d=(gH-bH)/gH*100; if(d>5)c.append("→ Sleep deficit drops HRV by ~").append(String.format("%.0f",d)).append("%\n");}

        // Body weight trend
        List<Map<String,Object>> bw = jdbcTemplate.queryForList(
            "SELECT date, value_avg as w FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='body_mass' ORDER BY date DESC LIMIT 10", uploadId);
        if(!bw.isEmpty()){c.append("\n=== Weight ===\n"); for(Map<String,Object> r:bw)c.append(r.get("date")).append(": ").append(r.get("w")).append("kg\n");}

        return c.toString();
    }

    public void saveSummary(UUID uploadId, AiAnswer answer) {
        jdbcTemplate.update("INSERT INTO health_report_summaries (id,upload_id,intent,conclusion,evidence,advice,disclaimer,created_at) VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?)",
                UUID.randomUUID(), uploadId, answer.intent().name(), answer.conclusion(),
                toJson(answer.evidence()), toJson(answer.advice()), answer.disclaimer(), java.sql.Timestamp.from(Instant.now()));
    }

    private String toJson(List<String> items) {
        try { return objectMapper.writeValueAsString(items); } catch (Exception e) { return "[]"; }
    }

    private AiAnswer parseResponse(String response, AiIntent fallback) {
        String cleaned = response.trim()
                .replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cleaned);
            return new AiAnswer(AiIntent.valueOf(node.get("intent").asText().toUpperCase()),
                    node.get("conclusion").asText(), objectMapper.convertValue(node.get("evidence"), List.class),
                    objectMapper.convertValue(node.get("advice"), List.class),
                    node.has("disclaimer") ? node.get("disclaimer").asText() : "本结果仅基于用户上传数据进行非医疗分析。");
        } catch (Exception e) {
            return new AiAnswer(fallback, response.length()>300?response.substring(0,300)+"...":response, List.of(), List.of(), "本结果仅基于用户上传数据进行非医疗分析。");
        }
    }
}
