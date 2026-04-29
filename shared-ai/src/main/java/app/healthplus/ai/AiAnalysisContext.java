package app.healthplus.ai;

import java.util.List;
import java.util.Map;

public record AiAnalysisContext(
        String question,
        String coverageStart,
        String coverageEnd,
        Map<String, Object> metrics,
        List<String> riskSignals
) {
}
