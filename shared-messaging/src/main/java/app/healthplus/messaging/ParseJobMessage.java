package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ParseJobMessage(
        UUID uploadId,
        UUID userId,
        String storageKey,
        String messageId,
        Instant createdAt
) implements Serializable {
}
