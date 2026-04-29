package app.healthplus.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateUploadRequest(
        @NotBlank String fileName,
        @Min(1) long fileSize
) {
}
