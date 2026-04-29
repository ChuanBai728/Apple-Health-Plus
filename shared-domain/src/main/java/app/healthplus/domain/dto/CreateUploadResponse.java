package app.healthplus.domain.dto;

import java.util.UUID;

public record CreateUploadResponse(
        UUID uploadId,
        String storageKey,
        String uploadUrl,
        String status
) {
}
