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

/**
 * AI 编排服务 -- 负责将聚合后的健康指标构建为结构化上下文，
 * 结合知识库 RAG 检索，调用 LLM 生成跨维度健康分析报告。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>从数据库提取该 upload 的健康洞察（指标统计、睡眠-恢复关联、体重趋势）</li>
 *   <li>通过 {@link HealthKnowledgeBase} 检索相关专业健康知识（pgvector 语义搜索）</li>
 *   <li>组装 system prompt，调用 Spring AI ChatClient 生成分析</li>
 *   <li>解析 LLM 返回的 JSON 结构，转换为 {@link AiAnswer} 领域对象</li>
 *   <li>将分析结果持久化到 health_report_summaries 表</li>
 * </ul>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>LLM 响应包含 intent / conclusion / evidence / advice / disclaimer 五个字段</li>
 *   <li>解析失败时回退为纯文本摘要，保证不会因 LLM 输出格式异常导致流程中断</li>
 *   <li>knowledgeBase.retrieve() 使用 pgvector 余弦相似度检索 Top-K 相关知识</li>
 * </ul>
 *
 * @see app.healthplus.aggregation.worker.AiJobListener  接收 AI 分析指令并调用本服务
 * @see app.healthplus.ai.HealthKnowledgeBase            健康知识库（pgvector RAG）
 */
@Service
public class AiOrchestratorService {

    /** 累积型指标集合：这些指标的聚合值应使用 sum 而非 avg（如步数、卡路里） */
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

    /**
     * 对指定 upload 执行 AI 健康分析。
     * 构建数据洞察上下文 -> RAG 检索知识库 -> 调用 LLM -> 解析响应。
     *
     * @param uploadId 上传记录 ID（UUID 字符串形式）
     * @param question 用户提问内容，null 时使用默认"分析整体健康状态"
     * @return 结构化的 AI 分析结果，包含意图、结论、证据、建议和免责声明
     */
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

    /**
     * 从数据库提取该 upload 的健康洞察数据，构建 LLM 可理解的文本上下文。
     * 包含三个维度：基础指标统计、睡眠-恢复关联分析、体重趋势。
     *
     * @param uploadId 上传记录 ID
     * @return 格式化的健康数据洞察文本
     */
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

        // 睡眠-恢复关联分析：对比充足睡眠(>=6.5h)与不足睡眠时的 HRV 和静息心率差异
        c.append("\n=== Sleep-Recovery Correlation ===\n");
        List<Map<String,Object>> sc = jdbcTemplate.queryForList(
            "SELECT sl.value_avg as sleep, COALESCE(hr.value_avg,0) as hrv, COALESCE(rh.value_avg,0) as rhr FROM health_metric_daily sl " +
            "LEFT JOIN health_metric_daily hr ON hr.upload_id=sl.upload_id AND hr.metric_key='heart_rate_variability_sdnn' AND hr.date=sl.date " +
            "LEFT JOIN health_metric_daily rh ON rh.upload_id=sl.upload_id AND rh.metric_key='resting_heart_rate' AND rh.date=sl.date " +
            "WHERE sl.upload_id=?::uuid AND sl.metric_key='sleep_duration' ORDER BY sl.date DESC LIMIT 30", uploadId);
        // gH/gR = 充足睡眠组的 HRV/RHR 累加值, bH/bR = 不足睡眠组的累加值
        // g/b = 各组天数，用于后续计算均值
        double gH=0,gR=0,bH=0,bR=0; int g=0,b=0;
        for (Map<String,Object> r: sc) {
            double s=((Number)r.get("sleep")).doubleValue(), hv=((Number)r.get("hrv")).doubleValue(), rh=((Number)r.get("rhr")).doubleValue();
            if(s>=6.5){gH+=hv;gR+=rh;g++;}else{bH+=hv;bR+=rh;b++;}
        }
        if(g>0&&b>0){gH/=g;gR/=g;bH/=b;bR/=b;
            c.append("Sleep≥6.5h: HRV=").append(String.format("%.0f",gH)).append("ms RHR=").append(String.format("%.0f",gR)).append("bpm (").append(g).append("d)\n");
            c.append("Sleep<6.5h: HRV=").append(String.format("%.0f",bH)).append("ms RHR=").append(String.format("%.0f",bR)).append("bpm (").append(b).append("d)\n");
            double d=(gH-bH)/gH*100; if(d>5)c.append("→ Sleep deficit drops HRV by ~").append(String.format("%.0f",d)).append("%\n");}

        // 体重趋势：最近10条记录，用于 LLM 判断体重变化方向
        List<Map<String,Object>> bw = jdbcTemplate.queryForList(
            "SELECT date, value_avg as w FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='body_mass' ORDER BY date DESC LIMIT 10", uploadId);
        if(!bw.isEmpty()){c.append("\n=== Weight ===\n"); for(Map<String,Object> r:bw)c.append(r.get("date")).append(": ").append(r.get("w")).append("kg\n");}

        return c.toString();
    }

    /**
     * 将 AI 分析结果持久化到 health_report_summaries 表。
     * evidence 和 advice 以 JSONB 格式存储，支持后续查询和展示。
     *
     * @param uploadId 关联的上传记录 ID
     * @param answer   AI 分析结果
     */
    public void saveSummary(UUID uploadId, AiAnswer answer) {
        jdbcTemplate.update("INSERT INTO health_report_summaries (id,upload_id,intent,conclusion,evidence,advice,disclaimer,created_at) VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?)",
                UUID.randomUUID(), uploadId, answer.intent().name(), answer.conclusion(),
                toJson(answer.evidence()), toJson(answer.advice()), answer.disclaimer(), java.sql.Timestamp.from(Instant.now()));
    }

    /**
     * 将字符串列表序列化为 JSON 数组字符串，用于存储到 JSONB 列。
     * 序列化失败时返回空数组 "[]"，保证不会阻断主流程。
     */
    private String toJson(List<String> items) {
        try { return objectMapper.writeValueAsString(items); } catch (Exception e) { return "[]"; }
    }

    /**
     * 解析 LLM 返回的 JSON 响应为 AiAnswer 对象。
     * 支持去除 markdown 代码块包裹（```json ... ```）。
     * 解析失败时回退为纯文本摘要，截断至300字符，保证流程不中断。
     *
     * @param response LLM 原始响应文本
     * @param fallback 解析失败时使用的默认意图类型
     * @return 结构化的 AI 分析结果
     */
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
