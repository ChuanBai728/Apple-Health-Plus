package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatSessionResponse(
        UUID sessionId,
        UUID uploadId,
        String title,
        Instant createdAt,
        List<ChatHistoryMessage> messages
) {
    public record ChatHistoryMessage(
            String role,
            String content,
            String intent,
            List<String> evidence,
            List<String> advice,
            String disclaimer,
            Instant createdAt
    ) {
    }
}
