package app.healthplus.domain.dto;

import java.util.List;

public record ChatMessageResponse(
        String intent,
        String conclusion,
        List<String> evidence,
        List<String> advice,
        String disclaimer
) {
}
