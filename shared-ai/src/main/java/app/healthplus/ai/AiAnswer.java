package app.healthplus.ai;

import java.util.List;

public record AiAnswer(
        AiIntent intent,
        String conclusion,
        List<String> evidence,
        List<String> advice,
        String disclaimer
) {
}
