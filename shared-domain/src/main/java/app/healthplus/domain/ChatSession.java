package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 聊天会话实体，对应数据库表 {@code chat_sessions}。
 * <p>
 * 每个会话关联一个上传记录（{@code uploadId}），用户基于该上传的健康数据
 * 与 AI 进行多轮对话。会话可由前端指定 ID 创建（用于 WebSocket 会话关联），
 * 也可自动生成。
 * </p>
 *
 * <h3>关键字段说明</h3>
 * <ul>
 *   <li>{@code uploadId} — 关联的上传记录 ID，可为 null（不绑定特定上传的通用会话）</li>
 *   <li>{@code title} — 会话标题，用于前端列表展示</li>
 * </ul>
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    /** JPA 要求的无参构造方法，仅供框架反射使用 */
    protected ChatSession() {
    }

    @Id
    private UUID id;

    /** 关联的上传记录 ID，可为 null 表示不绑定特定上传 */
    @Column
    private UUID uploadId;

    /** 会话标题，用于前端会话列表展示，最大 256 字符 */
    @Column(length = 256)
    private String title;

    /** 会话创建时间 */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * 工厂方法：使用指定 ID 创建会话。
     * <p>适用于前端已生成会话 ID（如 WebSocket 场景）的情况。</p>
     *
     * @param id       指定的会话 ID
     * @param uploadId 关联的上传记录 ID，可为 null
     * @param title    会话标题
     * @return 初始化完成的 ChatSession 实例（尚未持久化）
     */
    public static ChatSession create(UUID id, UUID uploadId, String title) {
        ChatSession session = new ChatSession();
        session.id = id;
        session.uploadId = uploadId;
        session.title = title;
        session.createdAt = Instant.now();
        return session;
    }

    /**
     * 工厂方法：自动生成 UUID 创建会话。
     *
     * @param uploadId 关联的上传记录 ID，可为 null
     * @param title    会话标题
     * @return 初始化完成的 ChatSession 实例（尚未持久化）
     */
    public static ChatSession create(UUID uploadId, String title) {
        return create(UUID.randomUUID(), uploadId, title);
    }

    public UUID getId() { return id; }
    public UUID getUploadId() { return uploadId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
}
