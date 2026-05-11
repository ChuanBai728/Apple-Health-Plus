package app.healthplus.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个指标数据点 DTO。
 * <p>表示某个时间点的健康指标值及其统计分析信息，用于折线图等可视化展示。
 * 使用 {@code @JsonInclude(NON_NULL)} 序列化时忽略 null 字段，简化仅含日期和值的场景。</p>
 *
 * @param date           数据日期（格式如 "2024-01-15"）
 * @param value          指标实际值
 * @param baselineAvg30d 近 30 天基线均值，用于对比参考
 * @param trendDelta7d   近 7 天趋势变化量（正值表示上升）
 * @param trendDelta30d  近 30 天趋势变化量
 * @param anomaly        是否为异常数据点
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricPoint(
        String date,
        Double value,
        Double baselineAvg30d,
        Double trendDelta7d,
        Double trendDelta30d,
        Boolean anomaly
) {
    /**
     * 简化构造器，仅需日期和值，其余统计字段默认为 null。
     */
    public MetricPoint(String date, Double value) {
        this(date, value, null, null, null, null);
    }
}
