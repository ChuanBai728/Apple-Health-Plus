package app.healthplus.domain.dto;

import java.util.List;

public record MetricSeriesResponse(
        String metricKey,
        String label,
        String granularity,
        List<MetricPoint> points,
        boolean anomaly,
        Double baselineAvg30d,
        Double trendDelta7d,
        Double trendDelta30d
) {
    public MetricSeriesResponse(String metricKey, String label, String granularity, List<MetricPoint> points) {
        this(metricKey, label, granularity, points, false, null, null, null);
    }
}
