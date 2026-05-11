package app.healthplus.domain;

/**
 * 健康指标聚合粒度枚举，用于指定 aggregation-worker 按何种时间维度计算指标。
 * <p>
 * 对应数据库中不同粒度的聚合表，前端图表根据该粒度决定 X 轴的刻度单位。
 * </p>
 */
public enum MetricGranularity {
    /** 日粒度：按自然日聚合，适合查看近期详细趋势 */
    DAILY,
    /** 周粒度：按自然周聚合，适合查看中期变化趋势 */
    WEEKLY,
    /** 月粒度：按自然月聚合，适合查看长期宏观趋势 */
    MONTHLY
}
