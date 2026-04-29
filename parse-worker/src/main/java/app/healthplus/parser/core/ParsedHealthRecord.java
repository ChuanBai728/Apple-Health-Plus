package app.healthplus.parser.core;

import java.time.Instant;

public record ParsedHealthRecord(
        String recordType,
        String metricKey,
        String categoryKey,
        String sourceName,
        Double valueNumeric,
        String valueText,
        String unit,
        Instant startAt,
        Instant endAt,
        String rawPayloadJson
) {
}
