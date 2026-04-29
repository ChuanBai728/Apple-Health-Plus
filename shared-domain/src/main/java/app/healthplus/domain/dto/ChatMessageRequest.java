package app.healthplus.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatMessageRequest(
        @NotBlank String question,
        UUID uploadId
) {
}
