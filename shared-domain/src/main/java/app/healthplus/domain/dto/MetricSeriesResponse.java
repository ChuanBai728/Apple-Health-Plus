package app.healthplus.domain.dto;

import java.util.List;

/**
 * 指标时序数据响应 DTO。
 * <p>用于返回某个健康指标在指定时间粒度下的完整数据序列，供前端绘制折线图。</p>
 *
 * @param metricKey   指标唯一标识键（如 "heart_rate"）
 * @param label       指标显示名称（如 "静息心率"）
 * @param granularity 数据粒度（如 "day"、"week"、"month"）
 * @param points      按时间排序的数据点列表
 */
public record MetricSeriesResponse(
        String metricKey,
        String label,
        String granularity,
        List<MetricPoint> points
) {
}
