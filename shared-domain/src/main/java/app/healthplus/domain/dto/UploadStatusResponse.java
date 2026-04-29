package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadStatusResponse(
        UUID uploadId,
        String status,
        String fileName,
        Instant createdAt,
        String lastError
) {
}
