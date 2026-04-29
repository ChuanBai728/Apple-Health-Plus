package app.healthplus.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricPoint(
        String date,
        Double value,
        Double baselineAvg30d,
        Double trendDelta7d,
        Double trendDelta30d,
        Boolean anomaly
) {
    public MetricPoint(String date, Double value) {
        this(date, value, null, null, null, null);
    }
}
