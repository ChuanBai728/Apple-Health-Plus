package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 聊天消息实体，对应数据库表 {@code chat_messages}。
 * <p>
 * 存储用户与 AI 助手之间的对话消息。助手消息额外携带意图分类、
 * 数据证据、健康建议和免责声明等结构化信息，供前端分层展示。
 * </p>
 *
 * <h3>关键字段说明</h3>
 * <ul>
 *   <li>{@code role} — 消息角色，取值为 {@code "user"} 或 {@code "assistant"}</li>
 *   <li>{@code intent} — AI 意图分类（如 QUERY_DATA、HEALTH_ADVICE 等），仅助手消息有值</li>
 *   <li>{@code evidence} — 引用的健康数据证据，用于支撑 AI 回答的可信度</li>
 *   <li>{@code advice} — AI 给出的健康建议文本</li>
 *   <li>{@code disclaimer} — 医疗免责声明，提醒用户 AI 建议不能替代专业诊断</li>
 * </ul>
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    /** JPA 要求的无参构造方法，仅供框架反射使用 */
    protected ChatMessage() {
    }

    @Id
    private UUID id;

    /** 所属会话 ID，外键关联 chat_sessions 表 */
    @Column(nullable = false)
    private UUID sessionId;

    /** 消息角色：{@code "user"} 表示用户输入，{@code "assistant"} 表示 AI 回复 */
    @Column(nullable = false, length = 32)
    private String role;

    /** 消息正文内容 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** AI 意图分类标识（如 QUERY_DATA、HEALTH_ADVICE 等），仅助手消息使用 */
    @Column(length = 32)
    private String intent;

    /** 引用的健康数据证据，支撑 AI 回答的数据来源 */
    @Column(columnDefinition = "text")
    private String evidence;

    /** AI 给出的结构化健康建议 */
    @Column(columnDefinition = "text")
    private String advice;

    /** 医疗免责声明，提醒 AI 建议不能替代专业医疗诊断 */
    @Column(columnDefinition = "text")
    private String disclaimer;

    /** 消息创建时间 */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * 工厂方法：创建用户消息。
     *
     * @param sessionId 所属会话 ID
     * @param content   用户输入的内容
     * @return 角色为 {@code "user"} 的消息实例
     */
    public static ChatMessage user(UUID sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.id = UUID.randomUUID();
        msg.sessionId = sessionId;
        msg.role = "user";
        msg.content = content;
        msg.createdAt = Instant.now();
        return msg;
    }

    /**
     * 工厂方法：创建 AI 助手消息，包含完整的结构化回复信息。
     *
     * @param sessionId 所属会话 ID
     * @param content   AI 回复的正文内容
     * @param intent    意图分类标识
     * @param evidence  数据证据
     * @param advice    健康建议
     * @param disclaimer 医疗免责声明
     * @return 角色为 {@code "assistant"} 的消息实例
     */
    public static ChatMessage assistant(UUID sessionId, String content, String intent, String evidence, String advice, String disclaimer) {
        ChatMessage msg = new ChatMessage();
        msg.id = UUID.randomUUID();
        msg.sessionId = sessionId;
        msg.role = "assistant";
        msg.content = content;
        msg.intent = intent;
        msg.evidence = evidence;
        msg.advice = advice;
        msg.disclaimer = disclaimer;
        msg.createdAt = Instant.now();
        return msg;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getIntent() { return intent; }
    public String getEvidence() { return evidence; }
    public String getAdvice() { return advice; }
    public String getDisclaimer() { return disclaimer; }
    public Instant getCreatedAt() { return createdAt; }
}
