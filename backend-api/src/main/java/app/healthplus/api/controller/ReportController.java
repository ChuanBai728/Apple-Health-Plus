package app.healthplus.api.controller;

import app.healthplus.api.service.HealthStateClassifier;
import app.healthplus.api.service.ReportQueryService;
import app.healthplus.api.service.ReportService;
import app.healthplus.domain.MetricGranularity;
import app.healthplus.domain.dto.MetricSeriesResponse;
import app.healthplus.domain.dto.OverviewResponse;
import java.util.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ReportService reportService;
    private final HealthStateClassifier classifier;

    public ReportController(ReportQueryService rqs, ReportService rs, HealthStateClassifier hsc) {
        this.reportQueryService = rqs; this.reportService = rs; this.classifier = hsc;
    }

    @GetMapping("/{reportId}/overview")
    @Cacheable(value = "overview", key = "#reportId", unless = "#result == null")
    public OverviewResponse getOverview(@PathVariable("reportId") UUID reportId) {
        return reportQueryService.getOverview(reportId);
    }

    @GetMapping("/{reportId}/metrics/{metricKey}")
    @Cacheable(value = "metrics", key = "#reportId + '_' + #metricKey + '_' + #granularity", unless = "#result == null")
    public MetricSeriesResponse getMetric(@PathVariable("reportId") UUID reportId,
            @PathVariable("metricKey") String metricKey,
            @RequestParam(name = "granularity", defaultValue = "DAILY") String granularity) {
        MetricGranularity g = MetricGranularity.valueOf(granularity.toUpperCase());
        if (metricKey.contains(",")) {
            String[] keys = metricKey.split(",", 2);
            return reportQueryService.getMetric(reportId, keys[0], keys.length>1?keys[1]:null, g);
        }
        return reportQueryService.getMetric(reportId, metricKey, g);
    }

    @GetMapping("/{reportId}/insight")
    @Cacheable(value = "insight", key = "#reportId + '_' + #type", unless = "#result == null", sync = true)
    public ReportService.ReportData getInsight(@PathVariable("reportId") UUID reportId,
            @RequestParam(name = "type", defaultValue = "weekly") String type) {
        return reportService.generateReport(reportId.toString(), type);
    }

    @GetMapping("/{reportId}/states")
    public Map<String,Object> getStates(@PathVariable("reportId") UUID reportId) {
        Map<String,Long> dist = classifier.getStateDistribution(reportId.toString());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("distribution", dist);
        return result;
    }
}
