package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    protected ChatSession() {
    }

    @Id
    private UUID id;

    @Column
    private UUID uploadId;

    @Column(length = 256)
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    public static ChatSession create(UUID id, UUID uploadId, String title) {
        ChatSession session = new ChatSession();
        session.id = id;
        session.uploadId = uploadId;
        session.title = title;
        session.createdAt = Instant.now();
        return session;
    }

    public static ChatSession create(UUID uploadId, String title) {
        return create(UUID.randomUUID(), uploadId, title);
    }

    public UUID getId() { return id; }
    public UUID getUploadId() { return uploadId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTitle(String title) { this.title = title; }
}
