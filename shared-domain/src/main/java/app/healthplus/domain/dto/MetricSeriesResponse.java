package app.healthplus.domain.dto;

import java.util.List;

public record MetricSeriesResponse(
        String metricKey,
        String label,
        String granularity,
        List<MetricPoint> points
) {
}
