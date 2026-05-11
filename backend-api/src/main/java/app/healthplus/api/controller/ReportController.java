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

/**
 * 健康报告查询控制器。
 * <p>
 * 职责：提供健康数据的多维度查询能力，包括总览概要、指标时序数据、
 * AI 洞察报告以及健康状态分布。
 * </p>
 * <p>
 * 对应 API 端点：{@code /api/v1/reports/{reportId}/...}
 * </p>
 * <p>
 * 缓存策略：总览、指标、洞察均使用 Spring Cache 缓存，避免重复计算。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** 报告查询服务 —— 负责从数据库读取聚合后的指标数据 */
    private final ReportQueryService reportQueryService;

    /** 报告生成服务 —— 负责调用 AI 生成洞察分析报告 */
    private final ReportService reportService;

    /** 健康状态分类器 —— 根据指标值判断用户所处的健康状态区间 */
    private final HealthStateClassifier classifier;

    public ReportController(ReportQueryService rqs, ReportService rs, HealthStateClassifier hsc) {
        this.reportQueryService = rqs; this.reportService = rs; this.classifier = hsc;
    }

    /**
     * 获取报告总览数据。
     * <p>
     * 返回关键健康指标的摘要信息（如步数、心率、睡眠等的当日/本周统计）。
     * </p>
     *
     * @param reportId 报告唯一标识
     * @return 总览响应，包含各维度的汇总指标
     */
    @GetMapping("/{reportId}/overview")
    @Cacheable(value = "overview", key = "#reportId", unless = "#result == null")
    public OverviewResponse getOverview(@PathVariable("reportId") UUID reportId) {
        return reportQueryService.getOverview(reportId);
    }

    /**
     * 获取单个或对比两个指标的时序数据。
     * <p>
     * 支持单指标查询和双指标对比查询（metricKey 以逗号分隔），
     * 粒度支持 DAILY / WEEKLY / MONTHLY。
     * </p>
     * <p>
     * 缓存 key 包含 reportId、metricKey 和 granularity 三部分，
     * 确保不同粒度的查询各自独立缓存。
     * </p>
     *
     * @param reportId   报告唯一标识
     * @param metricKey  指标键名，如 "step_count"；若含逗号则为对比模式，如 "step_count,active_energy"
     * @param granularity 时间粒度，默认 DAILY
     * @return 指标时序响应，包含日期列表和数值列表
     */
    @GetMapping("/{reportId}/metrics/{metricKey}")
    @Cacheable(value = "metrics", key = "#reportId + '_' + #metricKey + '_' + #granularity", unless = "#result == null")
    public MetricSeriesResponse getMetric(@PathVariable("reportId") UUID reportId,
            @PathVariable("metricKey") String metricKey,
            @RequestParam(name = "granularity", defaultValue = "DAILY") String granularity) {
        // 将字符串转为枚举，统一大小写处理
        MetricGranularity g = MetricGranularity.valueOf(granularity.toUpperCase());
        // 判断是否为双指标对比模式（metricKey 含逗号）
        if (metricKey.contains(",")) {
            String[] keys = metricKey.split(",", 2);
            return reportQueryService.getMetric(reportId, keys[0], keys.length>1?keys[1]:null, g);
        }
        // 单指标查询
        return reportQueryService.getMetric(reportId, metricKey, g);
    }

    /**
     * 获取 AI 生成的健康洞察报告。
     * <p>
     * 根据报告数据，调用 AI 服务生成结构化的健康分析与建议。
     * sync=true 表示同一时刻对相同参数的并发请求只执行一次计算，
     * 防止缓存击穿（thundering herd）。
     * </p>
     *
     * @param reportId 报告唯一标识
     * @param type     报告类型，默认 "weekly"，可选 daily / weekly / monthly
     * @return 包含 AI 洞察文本的报告数据
     */
    @GetMapping("/{reportId}/insight")
    @Cacheable(value = "insight", key = "#reportId + '_' + #type", unless = "#result == null", sync = true)
    public ReportService.ReportData getInsight(@PathVariable("reportId") UUID reportId,
            @RequestParam(name = "type", defaultValue = "weekly") String type) {
        return reportService.generateReport(reportId.toString(), type);
    }

    /**
     * 获取健康状态分布统计。
     * <p>
     * 返回各健康状态（如"优秀"/"良好"/"需关注"等）对应的指标数量分布。
     * </p>
     *
     * @param reportId 报告唯一标识
     * @return 包含 "distribution" 键的 Map，值为状态名到数量的映射
     */
    @GetMapping("/{reportId}/states")
    public Map<String,Object> getStates(@PathVariable("reportId") UUID reportId) {
        // 调用分类器计算各健康状态的指标分布
        Map<String,Long> dist = classifier.getStateDistribution(reportId.toString());
        // 使用 LinkedHashMap 保证返回字段顺序稳定
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("distribution", dist);
        return result;
    }
}
