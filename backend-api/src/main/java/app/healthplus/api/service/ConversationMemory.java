package app.healthplus.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 对话记忆服务 - 基于 Redis 的滑动窗口 + 摘要压缩记忆机制。
 *
 * <p>职责：
 * <ul>
 *   <li>管理对话上下文长度，避免超出 LLM 的 token 限制</li>
 *   <li>保留最近 N 条完整消息（滑动窗口）</li>
 *   <li>将较早的消息压缩为摘要，保留关键信息</li>
 *   <li>通过 Redis 持久化，支持分布式部署</li>
 * </ul>
 *
 * <p>设计思路：
 * <ul>
 *   <li>滑动窗口策略：保留最近 10 条完整消息，确保即时上下文完整</li>
 *   <li>摘要压缩：当窗口溢出时，提取最旧的 5 条消息进行 LLM 摘要</li>
 *   <li>分布式锁：使用 SETNX 防止并发写入导致数据不一致</li>
 *   <li>自动过期：Redis TTL 24 小时，自动清理过期会话</li>
 * </ul>
 *
 * <p>与其他组件的关系：
 * <ul>
 *   <li>被 ChatService 调用，管理每次问答的对话上下文</li>
 *   <li>使用 StringRedisTemplate 操作 Redis</li>
 *   <li>使用 ChatClient 调用 LLM 进行摘要生成</li>
 * </ul>
 *
 * @author Health+ Team
 * @see ChatService
 */
@Service
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

    /** 滑动窗口大小 - 保留最近 10 条完整消息 */
    private static final int WINDOW_SIZE = 10;

    /** 摘要批次大小 - 每次压缩 5 条消息 */
    private static final int SUMMARIZE_BATCH = 5;

    /** Redis 键过期时间 - 24 小时后自动清理 */
    private static final Duration TTL = Duration.ofHours(24);

    /** 分布式锁过期时间 - 10 秒超时防止死锁 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 获取锁的最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /** 重试间隔基数（毫秒），实际间隔 = RETRY_DELAY_MS * attempt */
    private static final long RETRY_DELAY_MS = 100;

    /** Redis 操作模板 */
    private final StringRedisTemplate redis;

    /** Spring AI 聊天客户端，用于调用 LLM 生成摘要 */
    private final ChatClient chatClient;

    /** JSON 序列化器 */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 构造函数 - 通过依赖注入初始化 Redis 和 AI 客户端。
     *
     * @param redis Redis 操作模板
     * @param cb    Spring AI 聊天客户端构建器
     */
    public ConversationMemory(StringRedisTemplate redis, ChatClient.Builder cb) {
        this.redis = redis;
        this.chatClient = cb.build();
    }

    /**
     * 记忆状态 - 包含摘要和滑动窗口的不可变记录类。
     *
     * <p>设计为不可变对象，每次修改都创建新实例，
     * 避免并发修改问题。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryState(String summary, Deque<Message> window) {}

    /**
     * 消息记录 - 存储单条对话消息。
     *
     * @param role      角色：user 或 assistant
     * @param content   消息内容
     * @param timestamp 消息时间戳（毫秒）
     */
    public record Message(String role, String content, long timestamp) {
        /** 无参构造函数，Jackson 反序列化需要 */
        public Message() { this(null, null, 0); }
    }

    /**
     * 加载或创建会话记忆状态。
     *
     * <p>从 Redis 加载现有状态，如果不存在或反序列化失败，
     * 则返回空的初始状态。
     *
     * @param sessionId 会话 ID
     * @return 记忆状态，包含摘要和滑动窗口
     */
    public MemoryState load(UUID sessionId) {
        String key = redisKey(sessionId);
        String json = redis.opsForValue().get(key);
        if (json != null && !json.isBlank()) {
            try {
                MemoryState state = mapper.readValue(json, MemoryState.class);
                if (state != null && state.window != null) return state;
            } catch (Exception e) {
                log.warn("Failed to deserialize conversation memory for session={}", sessionId, e);
            }
        }
        // 返回空的初始状态
        return new MemoryState("", new ArrayDeque<>());
    }

    /**
     * 添加消息到记忆 - 带分布式锁保护。
     *
     * <p>处理流程：
     * <ol>
     *   <li>尝试获取分布式锁（SETNX）</li>
     *   <li>获取成功则执行添加操作</li>
     *   <li>获取失败则重试（最多 3 次，指数退避）</li>
     *   <li>所有重试失败后强制执行（降级策略）</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @param role      角色：user 或 assistant
     * @param content   消息内容
     */
    public void addMessage(UUID sessionId, String role, String content) {
        String lockKey = redisLockKey(sessionId);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // 尝试获取分布式锁，SETNX 语义
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(lockKey, Thread.currentThread().getName(), LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    doAddMessage(sessionId, role, content);
                    return;
                } finally {
                    // 释放锁
                    redis.delete(lockKey);
                }
            }
            // 锁获取失败，指数退避重试
            log.debug("Failed to acquire lock for session={}, attempt={}", sessionId, attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS * (attempt + 1)); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for lock", e);
            }
        }
        // 所有重试失败，降级执行（保证功能可用性）
        log.error("Could not acquire lock for session={} after {} attempts", sessionId, MAX_RETRIES);
        doAddMessage(sessionId, role, content);
    }

    /**
     * 实际执行添加消息的内部方法。
     *
     * <p>核心逻辑：
     * <ol>
     *   <li>加载当前记忆状态</li>
     *   <li>将新消息添加到窗口末尾</li>
     *   <li>如果窗口溢出，提取最旧的批次进行摘要压缩</li>
     *   <li>保存更新后的状态</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @param role      角色
     * @param content   消息内容
     */
    private void doAddMessage(UUID sessionId, String role, String content) {
        MemoryState state = load(sessionId);
        // 将新消息添加到窗口末尾
        state.window().addLast(new Message(role, content, System.currentTimeMillis()));

        // 检查窗口是否溢出
        if (state.window().size() > WINDOW_SIZE) {
            // 提取最旧的批次消息进行摘要压缩
            List<Message> batch = new ArrayList<>();
            for (int i = 0; i < SUMMARIZE_BATCH && !state.window().isEmpty(); i++) {
                batch.add(state.window().pollFirst());
            }
            // 生成新摘要，与现有摘要合并
            String newSummary = summarizeBatch(batch, state.summary());
            state = new MemoryState(newSummary, state.window());
        }

        save(sessionId, state);
    }

    /**
     * 构建 LLM 提示词的上下文字符串。
     *
     * <p>输出格式：
     * <pre>
     * 【对话历史摘要】之前的对话摘要...
     * 【最近对话】
     * user: 用户消息
     * assistant: AI 回复
     * </pre>
     *
     * @param sessionId 会话 ID
     * @return 格式化的上下文字符串，用于 LLM 提示词
     */
    public String buildContext(UUID sessionId) {
        MemoryState state = load(sessionId);
        StringBuilder sb = new StringBuilder();
        // 添加历史摘要（如果有）
        if (state.summary() != null && !state.summary().isBlank()) {
            sb.append("【对话历史摘要】").append(state.summary()).append("\n");
        }
        // 添加滑动窗口中的最近消息
        sb.append("【最近对话】\n");
        for (Message m : state.window()) {
            sb.append(m.role()).append(": ").append(m.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 清除会话记忆。
     *
     * @param sessionId 会话 ID
     */
    public void clear(UUID sessionId) {
        redis.delete(redisKey(sessionId));
    }

    /**
     * 保存记忆状态到 Redis。
     *
     * @param sessionId 会话 ID
     * @param state     记忆状态
     */
    private void save(UUID sessionId, MemoryState state) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set(redisKey(sessionId), json, TTL);
        } catch (Exception e) {
            log.error("Failed to save conversation memory for session={}", sessionId, e);
        }
    }

    /**
     * 批量摘要压缩 - 使用 LLM 将多条消息压缩为一句摘要。
     *
     * <p>设计策略：
     * <ul>
     *   <li>将新批次消息与现有摘要合并生成新摘要</li>
     *   <li>失败时回退为简单拼接（保留原始文本的前 200 字符）</li>
     *   <li>摘要结果去除换行符，保持单行文本</li>
     * </ul>
     *
     * @param batch           待压缩的消息批次
     * @param existingSummary 现有的历史摘要
     * @return 压缩后的新摘要
     */
    private String summarizeBatch(List<Message> batch, String existingSummary) {
        if (batch.isEmpty()) return existingSummary;
        // 构建对话文本
        StringBuilder conv = new StringBuilder();
        for (Message m : batch) conv.append(m.role()).append(": ").append(m.content()).append("\n");

        // 构建摘要提示词
        String prompt = "将以下对话压缩为一句中文摘要，保留关键信息：\n" + conv;
        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt += "\n之前摘要：" + existingSummary + "\n请合并为新的一句摘要。";
        }

        try {
            // 调用 LLM 生成摘要
            String result = chatClient.prompt().user(prompt).call().content();
            return result != null ? result.replaceAll("\\n", " ").trim() : existingSummary;
        } catch (Exception e) {
            // LLM 调用失败，回退为简单拼接
            log.error("Summarization LLM call failed", e);
            return existingSummary + (existingSummary.isEmpty() ? "" : "；")
                    + conv.toString().replace('\n', ' ').substring(0, Math.min(200, conv.length()));
        }
    }

    /**
     * 生成 Redis 键 - 会话记忆存储键。
     *
     * @param sessionId 会话 ID
     * @return Redis 键，格式：chat:memory:{sessionId}
     */
    private String redisKey(UUID sessionId) { return "chat:memory:" + sessionId; }

    /**
     * 生成 Redis 键 - 分布式锁键。
     *
     * @param sessionId 会话 ID
     * @return Redis 锁键，格式：chat:memory:lock:{sessionId}
     */
    private String redisLockKey(UUID sessionId) { return "chat:memory:lock:" + sessionId; }
}
