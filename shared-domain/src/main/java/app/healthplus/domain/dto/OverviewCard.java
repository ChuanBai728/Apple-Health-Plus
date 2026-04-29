package app.healthplus.domain.dto;

import java.util.List;

public record OverviewCard(
        String metricKey,
        String label,
        Double latest,
        String unit,
        Double trend30d,
        boolean anomaly,
        List<MetricPoint> recentPoints
) {
}
