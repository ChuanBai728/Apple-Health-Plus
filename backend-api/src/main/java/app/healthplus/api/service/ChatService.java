package app.healthplus.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.healthplus.ai.AiAnswer;
import app.healthplus.ai.AiIntent;
import app.healthplus.ai.HealthKnowledgeBase;
import app.healthplus.domain.ChatMessage;
import app.healthplus.domain.ChatSession;
import app.healthplus.domain.dto.*;
import app.healthplus.api.repository.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','apple_exercise_time','apple_stand_time','workout'," +
            "'headphone_audio_exposure','time_in_daylight','walking_running_distance','sleep_duration'";

    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final HealthKnowledgeBase knowledgeBase;
    private final ConversationMemory memory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate,
                       ChatClient.Builder chatClientBuilder,
                       ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       HealthKnowledgeBase knowledgeBase,
                       ConversationMemory memory) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = chatClientBuilder.build();
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.knowledgeBase = knowledgeBase;
        this.memory = memory;
    }

    @Transactional
    public ChatMessageResponse ask(UUID sessionId, ChatMessageRequest request) {
        sessionRepository.findById(sessionId)
                .orElseGet(() -> sessionRepository.save(ChatSession.create(sessionId, request.uploadId(), request.question())));
        messageRepository.save(ChatMessage.user(sessionId, request.question()));
        memory.addMessage(sessionId, "user", request.question());  // sliding window + summarize

        String history = memory.buildContext(sessionId);  // Redis-backed window + summary
        String uploadId = resolveUploadId(request.uploadId());
        AiIntent intent = classifyIntent(request.question());
        String context = buildInsightContext(uploadId, request.question());
        String ragKnowledge = HealthKnowledgeBase.formatContext(
                knowledgeBase.retrieve(request.question(), HealthKnowledgeBase.intentToCategory(intent), 4));

        String systemPrompt = """
                You are a professional sports medicine expert and personal health coach.
                Use the provided professional health knowledge as your reference framework.

                Professional Health Knowledge Reference:
                %s

                Guidelines:
                - Base your analysis on BOTH user data AND professional knowledge
                - Cross-reference different metrics to find hidden correlations
                - Cite specific reference ranges when relevant
                - Be professional yet warm
                - Always include a disclaimer

                Conversation context (sliding window + summary): %s

                Respond in JSON:
                {
                  "intent": "OVERALL_SUMMARY|SLEEP_ANALYSIS|RECOVERY_ANALYSIS|ACTIVITY_TREND|HEART_CARDIOVASCULAR|WEIGHT_CHANGE|RISK_SIGNAL",
                  "conclusion": "1-3 sentence main finding with specific numbers",
                  "evidence": ["observation 1 with data", "observation 2", "observation 3"],
                  "advice": ["specific actionable step 1", "specific actionable step 2"],
                  "disclaimer": "本结果仅基于用户上传数据进行非医疗分析。"
                }""".formatted(ragKnowledge, history);

        String userPrompt = """
                Health data insights and context:
                %s

                User question: %s""".formatted(context, request.question());

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        AiAnswer answer = parseResponse(response, intent);
        memory.addMessage(sessionId, "assistant", answer.conclusion());  // save AI reply to memory

        try {
            messageRepository.save(ChatMessage.assistant(sessionId, answer.conclusion(),
                    answer.intent().name(), objectMapper.writeValueAsString(answer.evidence()),
                    objectMapper.writeValueAsString(answer.advice()), answer.disclaimer()));
        } catch (JsonProcessingException ignored) {}

        return new ChatMessageResponse(answer.intent().name().toLowerCase(),
                answer.conclusion(), answer.evidence(), answer.advice(), answer.disclaimer());
    }

    private String buildInsightContext(String uploadId, String question) {
        if (uploadId == null) return "No data available.";

        StringBuilder ctx = new StringBuilder();

        // 1. Basic stats per key metric
        ctx.append("=== Core Metrics Summary ===\n");
        List<Map<String, Object>> stats = jdbcTemplate.queryForList(
                """
                WITH ranked AS (
                    SELECT metric_key,
                           case when metric_key in (%s) then value_sum else value_avg end as val,
                           value_min, value_max, date,
                           row_number() OVER (PARTITION BY metric_key ORDER BY date DESC) as rn
                    FROM health_metric_daily WHERE upload_id=?::uuid AND value_avg IS NOT NULL
                ),
                latest_vals AS (
                    SELECT metric_key, val as last_val FROM ranked WHERE rn=1
                ),
                week_vals AS (
                    SELECT metric_key, val as week_ago_val FROM ranked WHERE rn=8
                ),
                month_vals AS (
                    SELECT metric_key, val as month_ago_val FROM ranked WHERE rn=31
                )
                SELECT r.metric_key,
                       round(avg(r.val)::numeric,2) as mean,
                       round(min(r.value_min)::numeric,2) as min_val,
                       round(max(r.value_max)::numeric,2) as max_val,
                       count(*) as days,
                       l.last_val, w.week_ago_val, m.month_ago_val
                FROM ranked r
                LEFT JOIN latest_vals l ON l.metric_key=r.metric_key
                LEFT JOIN week_vals w ON w.metric_key=r.metric_key
                LEFT JOIN month_vals m ON m.metric_key=r.metric_key
                GROUP BY r.metric_key, l.last_val, w.week_ago_val, m.month_ago_val
                ORDER BY days DESC
                """.replace("%s", CUM_SET), uploadId);
        for (Map<String, Object> r : stats) {
            String mk = (String) r.get("metric_key");
            ctx.append("- ").append(mk).append(": mean=").append(r.get("mean"))
               .append(", min=").append(r.get("min_val")).append(", max=").append(r.get("max_val"))
               .append(", days=").append(r.get("days"));
            if (r.get("last_val") != null) ctx.append(", latest=").append(r.get("last_val"));
            if (r.get("week_ago_val") != null) ctx.append(", 7d_ago=").append(r.get("week_ago_val"));
            if (r.get("month_ago_val") != null) ctx.append(", 30d_ago=").append(r.get("month_ago_val"));
            ctx.append("\n");
        }

        // 2. Sleep vs Recovery correlation
        ctx.append("\n=== Sleep vs Recovery Cross-Analysis ===\n");
        List<Map<String, Object>> sleepRecovery = jdbcTemplate.queryForList(
                """
                SELECT d.date, d.value_avg as sleep_h,
                       COALESCE(h.value_avg, 0) as hrv,
                       COALESCE(r.value_avg, 0) as rhr
                FROM health_metric_daily d
                LEFT JOIN health_metric_daily h ON h.upload_id=d.upload_id AND h.metric_key='heart_rate_variability_sdnn' AND h.date=d.date
                LEFT JOIN health_metric_daily r ON r.upload_id=d.upload_id AND r.metric_key='resting_heart_rate' AND r.date=d.date
                WHERE d.upload_id=?::uuid AND d.metric_key='sleep_duration'
                ORDER BY d.date DESC LIMIT 30
                """, uploadId);
        if (!sleepRecovery.isEmpty()) {
            double avgHrvGood = 0, avgHrvBad = 0, avgRhrGood = 0, avgRhrBad = 0;
            int goodCount = 0, badCount = 0;
            for (Map<String, Object> r : sleepRecovery) {
                double sleep = ((Number) r.get("sleep_h")).doubleValue();
                double hrv = ((Number) r.get("hrv")).doubleValue();
                double rhr = ((Number) r.get("rhr")).doubleValue();
                if (sleep >= 6.5) { avgHrvGood += hrv; avgRhrGood += rhr; goodCount++; }
                else { avgHrvBad += hrv; avgRhrBad += rhr; badCount++; }
            }
            if (goodCount > 0 && badCount > 0) {
                avgHrvGood /= goodCount; avgHrvBad /= badCount;
                avgRhrGood /= goodCount; avgRhrBad /= badCount;
                ctx.append("Sleep ≥6.5h: avg HRV=").append(String.format("%.1f", avgHrvGood))
                   .append("ms, avg RHR=").append(String.format("%.1f", avgRhrGood))
                   .append("bpm (").append(goodCount).append(" days)\n");
                ctx.append("Sleep <6.5h: avg HRV=").append(String.format("%.1f", avgHrvBad))
                   .append("ms, avg RHR=").append(String.format("%.1f", avgRhrBad))
                   .append("bpm (").append(badCount).append(" days)\n");
                double hrvDrop = (avgHrvGood - avgHrvBad) / avgHrvGood * 100;
                if (hrvDrop > 5) ctx.append("→ Sleep <6.5h correlates with ").append(String.format("%.0f", hrvDrop))
                   .append("% lower HRV — recovery is significantly impacted.\n");
            }
        }

        // 3. Activity level trend
        ctx.append("\n=== Activity & Exercise Trend ===\n");
        List<Map<String, Object>> activityTrend = jdbcTemplate.queryForList(
                """
                SELECT date, value_sum as steps FROM health_metric_daily
                WHERE upload_id=?::uuid AND metric_key='step_count'
                ORDER BY date DESC LIMIT 14
                """, uploadId);
        if (!activityTrend.isEmpty()) {
            double recentAvg = activityTrend.stream().limit(7).mapToDouble(r -> ((Number)r.get("steps")).doubleValue()).average().orElse(0);
            double prevAvg = activityTrend.stream().skip(7).mapToDouble(r -> ((Number)r.get("steps")).doubleValue()).average().orElse(0);
            ctx.append("Recent 7d avg steps: ").append(String.format("%.0f", recentAvg))
               .append(", previous 7d: ").append(String.format("%.0f", prevAvg));
            if (prevAvg > 0) ctx.append(" (").append(String.format("%+.0f%%", (recentAvg-prevAvg)/prevAvg*100)).append(")\n");
            else ctx.append("\n");
        }

        // 4. VO2 max trend (fitness indicator)
        List<Map<String, Object>> vo2 = jdbcTemplate.queryForList(
                "SELECT date, value_avg FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='vo2max' ORDER BY date DESC LIMIT 5",
                uploadId);
        if (!vo2.isEmpty()) {
            ctx.append("\n=== Cardio Fitness (VO2max) ===\n");
            for (Map<String, Object> r : vo2) ctx.append("  ").append(r.get("date")).append(": ").append(r.get("value_avg")).append("\n");
        }

        // 5. Body composition
        List<Map<String, Object>> body = jdbcTemplate.queryForList(
                "SELECT date, value_avg as weight FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='body_mass' ORDER BY date DESC LIMIT 10",
                uploadId);
        if (!body.isEmpty()) {
            ctx.append("\n=== Body Weight Trend ===\n");
            for (Map<String, Object> r : body) ctx.append("  ").append(r.get("date")).append(": ").append(r.get("weight")).append("kg\n");
            double first = ((Number)body.get(body.size()-1).get("weight")).doubleValue();
            double last = ((Number)body.get(0).get("weight")).doubleValue();
            ctx.append("  Change: ").append(String.format("%+.1f", last-first)).append("kg over last ").append(body.size()).append(" records\n");
        }

        return ctx.toString();
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(UUID sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionResponse(session, messages);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions() {
        return sessionRepository.findAll().stream()
                .map(s -> {
                    List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId());
                    return toSessionResponse(s, msgs);
                }).collect(Collectors.toList());
    }

    private String resolveUploadId(UUID uploadId) {
        if (uploadId != null) return uploadId.toString();
        List<Map<String, Object>> latest = jdbcTemplate.queryForList(
                "SELECT id FROM uploads WHERE status='READY' ORDER BY created_at DESC LIMIT 1");
        return latest.isEmpty() ? null : latest.get(0).get("id").toString();
    }

    private String loadHistory(UUID sessionId) {
        List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (msgs.isEmpty()) return "(new conversation)";
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : msgs) sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        return sb.toString();
    }

    private AiIntent classifyIntent(String question) {
        if (question == null) return AiIntent.OVERALL_SUMMARY;
        String q = question.toLowerCase();
        if (q.contains("sleep")||q.contains("睡眠")||q.contains("睡")) return AiIntent.SLEEP_ANALYSIS;
        if (q.contains("recovery")||q.contains("恢复")||q.contains("hrv")) return AiIntent.RECOVERY_ANALYSIS;
        if (q.contains("activity")||q.contains("活动")||q.contains("运动")||q.contains("步")) return AiIntent.ACTIVITY_TREND;
        if (q.contains("heart")||q.contains("心率")||q.contains("心脏")) return AiIntent.HEART_CARDIOVASCULAR;
        if (q.contains("weight")||q.contains("体重")||q.contains("bmi")) return AiIntent.WEIGHT_CHANGE;
        if (q.contains("risk")||q.contains("风险")||q.contains("异常")) return AiIntent.RISK_SIGNAL;
        return AiIntent.OVERALL_SUMMARY;
    }

    private AiAnswer parseResponse(String response, AiIntent fallback) {
        // Strip markdown code blocks that DeepSeek sometimes wraps JSON in
        String cleaned = response.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cleaned);
            return new AiAnswer(
                    AiIntent.valueOf(node.get("intent").asText().toUpperCase()),
                    node.get("conclusion").asText(),
                    objectMapper.convertValue(node.get("evidence"), List.class),
                    objectMapper.convertValue(node.get("advice"), List.class),
                    node.has("disclaimer") ? node.get("disclaimer").asText() : "本结果仅基于用户上传数据进行非医疗分析。");
        } catch (Exception e) {
            return new AiAnswer(fallback,
                    response.length() > 300 ? response.substring(0,300)+"..." : response,
                    List.of(), List.of(), "本结果仅基于用户上传数据进行非医疗分析。");
        }
    }

    private ChatSessionResponse toSessionResponse(ChatSession session, List<ChatMessage> messages) {
        List<ChatSessionResponse.ChatHistoryMessage> h = messages.stream().map(m -> {
            List<String> ev = parseJsonList(m.getEvidence());
            List<String> ad = parseJsonList(m.getAdvice());
            return new ChatSessionResponse.ChatHistoryMessage(m.getRole(), m.getContent(), m.getIntent(), ev, ad, m.getDisclaimer(), m.getCreatedAt());
        }).collect(Collectors.toList());
        return new ChatSessionResponse(session.getId(), session.getUploadId(), session.getTitle(), session.getCreatedAt(), h);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(json); }
    }
}
