package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    protected ChatMessage() {
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 32)
    private String intent;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(columnDefinition = "text")
    private String advice;

    @Column(columnDefinition = "text")
    private String disclaimer;

    @Column(nullable = false)
    private Instant createdAt;

    public static ChatMessage user(UUID sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.id = UUID.randomUUID();
        msg.sessionId = sessionId;
        msg.role = "user";
        msg.content = content;
        msg.createdAt = Instant.now();
        return msg;
    }

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
