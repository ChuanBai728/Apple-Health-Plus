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
 * Sliding-window + summarization memory backed by Redis.
 * - Keeps last 10 messages in full (window)
 * - Older messages are compressed into a running summary
 * - Redis TTL: 24h auto-expire
 * - Uses SETNX-based distributed lock to prevent race conditions
 */
@Service
public class ConversationMemory {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemory.class);

    private static final int WINDOW_SIZE = 10;
    private static final int SUMMARIZE_BATCH = 5;
    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final StringRedisTemplate redis;
    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConversationMemory(StringRedisTemplate redis, ChatClient.Builder cb) {
        this.redis = redis;
        this.chatClient = cb.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryState(String summary, Deque<Message> window) {}

    public record Message(String role, String content, long timestamp) {
        public Message() { this(null, null, 0); }
    }

    /** Load or create memory for a session. */
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
        return new MemoryState("", new ArrayDeque<>());
    }

    /** Add a message, auto-summarize if window overflows. Uses distributed lock to prevent race conditions. */
    public void addMessage(UUID sessionId, String role, String content) {
        String lockKey = redisLockKey(sessionId);
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(lockKey, Thread.currentThread().getName(), LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                try {
                    doAddMessage(sessionId, role, content);
                    return;
                } finally {
                    redis.delete(lockKey);
                }
            }
            log.debug("Failed to acquire lock for session={}, attempt={}", sessionId, attempt + 1);
            try { Thread.sleep(RETRY_DELAY_MS * (attempt + 1)); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for lock", e);
            }
        }
        log.error("Could not acquire lock for session={} after {} attempts", sessionId, MAX_RETRIES);
        doAddMessage(sessionId, role, content);
    }

    private void doAddMessage(UUID sessionId, String role, String content) {
        MemoryState state = load(sessionId);
        state.window().addLast(new Message(role, content, System.currentTimeMillis()));

        if (state.window().size() > WINDOW_SIZE) {
            List<Message> batch = new ArrayList<>();
            for (int i = 0; i < SUMMARIZE_BATCH && !state.window().isEmpty(); i++) {
                batch.add(state.window().pollFirst());
            }
            String newSummary = summarizeBatch(batch, state.summary());
            state = new MemoryState(newSummary, state.window());
        }

        save(sessionId, state);
    }

    /** Build context string for the LLM prompt. */
    public String buildContext(UUID sessionId) {
        MemoryState state = load(sessionId);
        StringBuilder sb = new StringBuilder();
        if (state.summary() != null && !state.summary().isBlank()) {
            sb.append("【对话历史摘要】").append(state.summary()).append("\n");
        }
        sb.append("【最近对话】\n");
        for (Message m : state.window()) {
            sb.append(m.role()).append(": ").append(m.content()).append("\n");
        }
        return sb.toString();
    }

    public void clear(UUID sessionId) {
        redis.delete(redisKey(sessionId));
    }

    private void save(UUID sessionId, MemoryState state) {
        try {
            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set(redisKey(sessionId), json, TTL);
        } catch (Exception e) {
            log.error("Failed to save conversation memory for session={}", sessionId, e);
        }
    }

    private String summarizeBatch(List<Message> batch, String existingSummary) {
        if (batch.isEmpty()) return existingSummary;
        StringBuilder conv = new StringBuilder();
        for (Message m : batch) conv.append(m.role()).append(": ").append(m.content()).append("\n");

        String prompt = "将以下对话压缩为一句中文摘要，保留关键信息：\n" + conv;
        if (existingSummary != null && !existingSummary.isBlank()) {
            prompt += "\n之前摘要：" + existingSummary + "\n请合并为新的一句摘要。";
        }

        try {
            String result = chatClient.prompt().user(prompt).call().content();
            return result != null ? result.replaceAll("\\n", " ").trim() : existingSummary;
        } catch (Exception e) {
            log.error("Summarization LLM call failed", e);
            return existingSummary + (existingSummary.isEmpty() ? "" : "；")
                    + conv.toString().replace('\n', ' ').substring(0, Math.min(200, conv.length()));
        }
    }

    private String redisKey(UUID sessionId) { return "chat:memory:" + sessionId; }

    private String redisLockKey(UUID sessionId) { return "chat:memory:lock:" + sessionId; }
}
