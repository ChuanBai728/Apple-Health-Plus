package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OverviewResponse(
        UUID reportId,
        String status,
        Coverage coverage,
        List<OverviewCard> cards,
        String headline
) {
    public record Coverage(
            long totalRecords,
            int metricCount,
            int sourceCount,
            Instant startDate,
            Instant endDate
    ) {}
}
