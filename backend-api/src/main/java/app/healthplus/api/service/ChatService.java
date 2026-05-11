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

/**
 * 聊天服务 - 健康数据智能问答的核心服务类。
 *
 * <p>职责：
 * <ul>
 *   <li>接收用户健康相关问题，整合多数据源生成专业回答</li>
 *   <li>管理聊天会话和消息的持久化存储</li>
 *   <li>调用 AI 模型进行意图识别和回答生成</li>
 *   <li>从数据库提取健康数据洞察，为 AI 提供上下文</li>
 * </ul>
 *
 * <p>设计思路：
 * <ul>
 *   <li>采用 RAG 架构：用户问题 → 意图分类 → 知识库检索 → 数据洞察 → AI 生成</li>
 *   <li>多层上下文构建：对话历史 + 健康数据 + 专业知识库</li>
 *   <li>滑动窗口记忆：通过 ConversationMemory 管理对话上下文长度</li>
 * </ul>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>依赖 ConversationMemory 管理对话记忆（Redis 持久化）</li>
 *   <li>依赖 HealthKnowledgeBase 进行 pgvector 向量检索</li>
 *   <li>依赖 ChatSessionRepository/ChatMessageRepository 持久化会话</li>
 *   <li>使用 JdbcTemplate 直接查询 health_metric_daily 表获取数据洞察</li>
 * </ul>
 *
 * @author Health+ Team
 * @see ConversationMemory
 * @see HealthKnowledgeBase
 */
@Service
public class ChatService {

    /**
     * 累积型指标的 metric_key 集合。
     * 这些指标使用 value_sum（累计值）而非 value_avg（平均值）进行统计，
     * 例如步数、能量消耗等应该看总量而非日均。
     */
    private static final String CUM_SET = "'step_count','active_energy_burned','basal_energy_burned','flights_climbed'," +
            "'distance_walking_running','apple_exercise_time','apple_stand_time','workout'," +
            "'headphone_audio_exposure','time_in_daylight','walking_running_distance','sleep_duration'";

    /** JDBC 模板，用于直接查询 health_metric_daily 表构建数据洞察 */
    private final JdbcTemplate jdbcTemplate;

    /** Spring AI 聊天客户端，封装了 DeepSeek API 调用 */
    private final ChatClient chatClient;

    /** 聊天会话仓储，管理会话生命周期 */
    private final ChatSessionRepository sessionRepository;

    /** 聊天消息仓储，持久化用户和 AI 的对话消息 */
    private final ChatMessageRepository messageRepository;

    /** 健康知识库，基于 pgvector 的向量 RAG 检索 */
    private final HealthKnowledgeBase knowledgeBase;

    /** 对话记忆服务，Redis 滑动窗口 + 摘要压缩 */
    private final ConversationMemory memory;

    /** JSON 序列化器，用于解析 AI 返回的结构化数据 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数 - 通过依赖注入初始化所有协作组件。
     *
     * @param jdbcTemplate         JDBC 模板，用于健康数据查询
     * @param chatClientBuilder    Spring AI 聊天客户端构建器
     * @param sessionRepository    会话仓储
     * @param messageRepository    消息仓储
     * @param knowledgeBase        健康知识库（pgvector RAG）
     * @param memory               对话记忆服务（Redis 滑动窗口）
     */
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

    /**
     * 处理用户健康问答请求 - 核心业务方法。
     *
     * <p>处理流程：
     * <ol>
     *   <li>确保会话存在，持久化用户消息到数据库和 Redis 记忆</li>
     *   <li>构建多层上下文：对话历史 + 健康数据洞察 + RAG 知识库</li>
     *   <li>调用 AI 模型生成结构化回答（JSON 格式）</li>
     *   <li>解析 AI 响应，持久化回答到数据库和 Redis 记忆</li>
     * </ol>
     *
     * @param sessionId 会话 ID，用于标识和关联对话
     * @param request   用户请求，包含问题内容和可选的 uploadId
     * @return 结构化的 AI 回答，包含意图、结论、证据、建议和免责声明
     */
    @Transactional
    public ChatMessageResponse ask(UUID sessionId, ChatMessageRequest request) {
        // 1. 确保会话存在 - 如果是新会话则创建
        sessionRepository.findById(sessionId)
                .orElseGet(() -> sessionRepository.save(ChatSession.create(sessionId, request.uploadId(), request.question())));

        // 2. 持久化用户消息到数据库和 Redis 滑动窗口记忆
        messageRepository.save(ChatMessage.user(sessionId, request.question()));
        memory.addMessage(sessionId, "user", request.question());  // sliding window + summarize

        // 3. 构建多层上下文
        String history = memory.buildContext(sessionId);  // Redis-backed window + summary
        String uploadId = resolveUploadId(request.uploadId());
        AiIntent intent = classifyIntent(request.question());
        String context = buildInsightContext(uploadId, request.question());  // 健康数据洞察
        String ragKnowledge = HealthKnowledgeBase.formatContext(
                knowledgeBase.retrieve(request.question(), HealthKnowledgeBase.intentToCategory(intent), 4));  // RAG 知识库检索

        // 4. 构建系统提示词 - 融合专业知识、对话历史和数据洞察
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

        // 5. 构建用户提示词 - 包含数据洞察和具体问题
        String userPrompt = """
                Health data insights and context:
                %s

                User question: %s""".formatted(context, request.question());

        // 6. 调用 AI 模型生成回答
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        // 7. 解析 AI 响应并持久化
        AiAnswer answer = parseResponse(response, intent);
        memory.addMessage(sessionId, "assistant", answer.conclusion());  // save AI reply to memory

        try {
            messageRepository.save(ChatMessage.assistant(sessionId, answer.conclusion(),
                    answer.intent().name(), objectMapper.writeValueAsString(answer.evidence()),
                    objectMapper.writeValueAsString(answer.advice()), answer.disclaimer()));
        } catch (JsonProcessingException ignored) {}

        // 8. 返回结构化响应
        return new ChatMessageResponse(answer.intent().name().toLowerCase(),
                answer.conclusion(), answer.evidence(), answer.advice(), answer.disclaimer());
    }

    /**
     * 构建健康数据洞察上下文 - 为 AI 提供用户健康数据的多维度分析。
     *
     * <p>该方法从 health_metric_daily 表提取关键健康指标，生成结构化的数据洞察，
     * 包括：核心指标统计、睡眠与恢复关联分析、活动趋势、VO2max、体重变化等。
     *
     * <p>设计特点：
     * <ul>
     *   <li>区分累积型指标（步数、能量）和平均型指标（心率、HRV）</li>
     *   <li>提供时间维度对比：最新值 vs 7天前 vs 30天前</li>
     *   <li>跨指标关联分析：睡眠质量与 HRV/RHR 的相关性</li>
     * </ul>
     *
     * @param uploadId 用户上传数据的 ID，用于关联健康数据
     * @param question 用户问题（预留，可用于针对性分析）
     * @return 格式化的数据洞察文本，作为 AI 提示词的一部分
     */
    private String buildInsightContext(String uploadId, String question) {
        if (uploadId == null) return "No data available.";

        StringBuilder ctx = new StringBuilder();

        // 1. 核心指标统计 - 使用 CTE 窗口函数获取每个指标的均值、极值、最新值、历史值
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
        // 遍历每个指标，输出统计摘要
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

        // 2. 睡眠与恢复关联分析 - 探索睡眠时长与 HRV/RHR 的相关性
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
            // 按睡眠时长分组：≥6.5h 为良好睡眠，<6.5h 为不足睡眠
            double avgHrvGood = 0, avgHrvBad = 0, avgRhrGood = 0, avgRhrBad = 0;
            int goodCount = 0, badCount = 0;
            for (Map<String, Object> r : sleepRecovery) {
                double sleep = ((Number) r.get("sleep_h")).doubleValue();
                double hrv = ((Number) r.get("hrv")).doubleValue();
                double rhr = ((Number) r.get("rhr")).doubleValue();
                if (sleep >= 6.5) { avgHrvGood += hrv; avgRhrGood += rhr; goodCount++; }
                else { avgHrvBad += hrv; avgRhrBad += rhr; badCount++; }
            }
            // 计算分组平均值并输出对比分析
            if (goodCount > 0 && badCount > 0) {
                avgHrvGood /= goodCount; avgHrvBad /= badCount;
                avgRhrGood /= goodCount; avgRhrBad /= badCount;
                ctx.append("Sleep ≥6.5h: avg HRV=").append(String.format("%.1f", avgHrvGood))
                   .append("ms, avg RHR=").append(String.format("%.1f", avgRhrGood))
                   .append("bpm (").append(goodCount).append(" days)\n");
                ctx.append("Sleep <6.5h: avg HRV=").append(String.format("%.1f", avgHrvBad))
                   .append("ms, avg RHR=").append(String.format("%.1f", avgRhrBad))
                   .append("bpm (").append(badCount).append(" days)\n");
                // 计算 HRV 下降幅度，超过 5% 则提示恢复受影响
                double hrvDrop = (avgHrvGood - avgHrvBad) / avgHrvGood * 100;
                if (hrvDrop > 5) ctx.append("→ Sleep <6.5h correlates with ").append(String.format("%.0f", hrvDrop))
                   .append("% lower HRV — recovery is significantly impacted.\n");
            }
        }

        // 3. 活动趋势分析 - 对比最近 7 天与前 7 天的步数变化
        ctx.append("\n=== Activity & Exercise Trend ===\n");
        List<Map<String, Object>> activityTrend = jdbcTemplate.queryForList(
                """
                SELECT date, value_sum as steps FROM health_metric_daily
                WHERE upload_id=?::uuid AND metric_key='step_count'
                ORDER BY date DESC LIMIT 14
                """, uploadId);
        if (!activityTrend.isEmpty()) {
            // 分别计算最近 7 天和前 7 天的平均步数
            double recentAvg = activityTrend.stream().limit(7).mapToDouble(r -> ((Number)r.get("steps")).doubleValue()).average().orElse(0);
            double prevAvg = activityTrend.stream().skip(7).mapToDouble(r -> ((Number)r.get("steps")).doubleValue()).average().orElse(0);
            ctx.append("Recent 7d avg steps: ").append(String.format("%.0f", recentAvg))
               .append(", previous 7d: ").append(String.format("%.0f", prevAvg));
            // 计算变化百分比
            if (prevAvg > 0) ctx.append(" (").append(String.format("%+.0f%%", (recentAvg-prevAvg)/prevAvg*100)).append(")\n");
            else ctx.append("\n");
        }

        // 4. VO2max 趋势 - 心肺适能指标，反映有氧运动能力
        List<Map<String, Object>> vo2 = jdbcTemplate.queryForList(
                "SELECT date, value_avg FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='vo2max' ORDER BY date DESC LIMIT 5",
                uploadId);
        if (!vo2.isEmpty()) {
            ctx.append("\n=== Cardio Fitness (VO2max) ===\n");
            for (Map<String, Object> r : vo2) ctx.append("  ").append(r.get("date")).append(": ").append(r.get("value_avg")).append("\n");
        }

        // 5. 体重趋势 - 最近 10 条记录及总体变化
        List<Map<String, Object>> body = jdbcTemplate.queryForList(
                "SELECT date, value_avg as weight FROM health_metric_daily WHERE upload_id=?::uuid AND metric_key='body_mass' ORDER BY date DESC LIMIT 10",
                uploadId);
        if (!body.isEmpty()) {
            ctx.append("\n=== Body Weight Trend ===\n");
            for (Map<String, Object> r : body) ctx.append("  ").append(r.get("date")).append(": ").append(r.get("weight")).append("kg\n");
            // 计算体重变化：最早记录 vs 最新记录
            double first = ((Number)body.get(body.size()-1).get("weight")).doubleValue();
            double last = ((Number)body.get(0).get("weight")).doubleValue();
            ctx.append("  Change: ").append(String.format("%+.1f", last-first)).append("kg over last ").append(body.size()).append(" records\n");
        }

        return ctx.toString();
    }

    /**
     * 获取单个聊天会话及其所有消息。
     *
     * @param sessionId 会话 ID
     * @return 会话详情，包含完整的消息历史
     * @throws IllegalArgumentException 如果会话不存在
     */
    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(UUID sessionId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return toSessionResponse(session, messages);
    }

    /**
     * 获取所有聊天会话列表。
     *
     * @return 所有会话的列表，每个会话包含消息历史
     */
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions() {
        return sessionRepository.findAll().stream()
                .map(s -> {
                    List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId());
                    return toSessionResponse(s, msgs);
                }).collect(Collectors.toList());
    }

    /**
     * 解析上传 ID - 如果未提供则自动选择最新的已就绪上传。
     *
     * <p>设计逻辑：用户可能未指定 uploadId，此时系统自动关联最新的健康数据，
     * 确保 AI 能够基于最新数据进行分析。
     *
     * @param uploadId 用户提供的上传 ID，可为 null
     * @return 有效的 uploadId 字符串，或 null（无可用数据）
     */
    private String resolveUploadId(UUID uploadId) {
        if (uploadId != null) return uploadId.toString();
        // 未提供 uploadId 时，查询最新的已就绪上传记录
        List<Map<String, Object>> latest = jdbcTemplate.queryForList(
                "SELECT id FROM uploads WHERE status='READY' ORDER BY created_at DESC LIMIT 1");
        return latest.isEmpty() ? null : latest.get(0).get("id").toString();
    }

    /**
     * 加载会话历史消息（旧方法，已被 ConversationMemory 替代）。
     *
     * @param sessionId 会话 ID
     * @return 格式化的历史消息文本
     */
    private String loadHistory(UUID sessionId) {
        List<ChatMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (msgs.isEmpty()) return "(new conversation)";
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : msgs) sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        return sb.toString();
    }

    /**
     * 基于关键词的意图分类 - 快速识别用户问题的健康领域。
     *
     * <p>设计特点：
     * <ul>
     *   <li>支持中英文关键词匹配</li>
     *   <li>优先匹配特定领域，最后回退到 OVERALL_SUMMARY</li>
     *   <li>用于指导 RAG 知识库的分类检索</li>
     * </ul>
     *
     * @param question 用户问题文本
     * @return 识别出的意图枚举值
     */
    private AiIntent classifyIntent(String question) {
        if (question == null) return AiIntent.OVERALL_SUMMARY;
        String q = question.toLowerCase();
        // 按优先级匹配：睡眠 → 恢复 → 活动 → 心脏 → 体重 → 风险 → 默认
        if (q.contains("sleep")||q.contains("睡眠")||q.contains("睡")) return AiIntent.SLEEP_ANALYSIS;
        if (q.contains("recovery")||q.contains("恢复")||q.contains("hrv")) return AiIntent.RECOVERY_ANALYSIS;
        if (q.contains("activity")||q.contains("活动")||q.contains("运动")||q.contains("步")) return AiIntent.ACTIVITY_TREND;
        if (q.contains("heart")||q.contains("心率")||q.contains("心脏")) return AiIntent.HEART_CARDIOVASCULAR;
        if (q.contains("weight")||q.contains("体重")||q.contains("bmi")) return AiIntent.WEIGHT_CHANGE;
        if (q.contains("risk")||q.contains("风险")||q.contains("异常")) return AiIntent.RISK_SIGNAL;
        return AiIntent.OVERALL_SUMMARY;
    }

    /**
     * 解析 AI 返回的 JSON 响应 - 提取结构化数据。
     *
     * <p>处理策略：
     * <ul>
     *   <li>先清理 DeepSeek 可能添加的 markdown 代码块标记</li>
     *   <li>尝试解析为标准 JSON 结构</li>
     *   <li>解析失败时回退为纯文本回答</li>
     * </ul>
     *
     * @param response AI 返回的原始文本
     * @param fallback 意图分类的回退值（当 JSON 解析失败时使用）
     * @return 结构化的 AI 回答对象
     */
    private AiAnswer parseResponse(String response, AiIntent fallback) {
        // 清理 DeepSeek 可能包裹的 markdown 代码块标记
        String cleaned = response.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");
        try {
            // 解析 JSON 并提取各字段
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cleaned);
            return new AiAnswer(
                    AiIntent.valueOf(node.get("intent").asText().toUpperCase()),
                    node.get("conclusion").asText(),
                    objectMapper.convertValue(node.get("evidence"), List.class),
                    objectMapper.convertValue(node.get("advice"), List.class),
                    node.has("disclaimer") ? node.get("disclaimer").asText() : "本结果仅基于用户上传数据进行非医疗分析。");
        } catch (Exception e) {
            // JSON 解析失败，回退为纯文本回答
            return new AiAnswer(fallback,
                    response.length() > 300 ? response.substring(0,300)+"..." : response,
                    List.of(), List.of(), "本结果仅基于用户上传数据进行非医疗分析。");
        }
    }

    /**
     * 将会话实体和消息列表转换为响应 DTO。
     *
     * @param session  会话实体
     * @param messages 消息列表
     * @return 会话响应 DTO，包含完整的消息历史
     */
    private ChatSessionResponse toSessionResponse(ChatSession session, List<ChatMessage> messages) {
        List<ChatSessionResponse.ChatHistoryMessage> h = messages.stream().map(m -> {
            List<String> ev = parseJsonList(m.getEvidence());
            List<String> ad = parseJsonList(m.getAdvice());
            return new ChatSessionResponse.ChatHistoryMessage(m.getRole(), m.getContent(), m.getIntent(), ev, ad, m.getDisclaimer(), m.getCreatedAt());
        }).collect(Collectors.toList());
        return new ChatSessionResponse(session.getId(), session.getUploadId(), session.getTitle(), session.getCreatedAt(), h);
    }

    /**
     * 安全解析 JSON 字符串为列表。
     *
     * @param json JSON 字符串，可为 null 或空
     * @return 解析后的字符串列表，解析失败时返回包含原始文本的单元素列表
     */
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(json); }
    }
}
