package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AiAnalysisCommand(
        UUID sessionId,
        UUID uploadId,
        UUID userId,
        String question,
        String messageId,
        Instant createdAt
) implements Serializable {
}
