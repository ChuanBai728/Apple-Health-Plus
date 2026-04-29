package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record AggregateJobMessage(
        UUID uploadId,
        UUID userId,
        String messageId,
        String triggeredBy,
        Instant createdAt
) implements Serializable {
}
