package app.healthplus.aggregation.config;

/**
 * RabbitMQ 队列名称常量类。
 * 集中管理所有队列名，避免硬编码分散在各处，便于统一修改和引用。
 *
 * <p>队列分为两组：</p>
 * <ul>
 *   <li>聚合相关：主队列 + 死信队列</li>
 *   <li>AI 分析相关：主队列 + 死信队列</li>
 * </ul>
 */
public final class QueueNames {
    public static final String AGGREGATE_QUEUE = "health.aggregate.queue";
    public static final String AGGREGATE_DLQ = "health.aggregate.dlq";
    public static final String AI_QUEUE = "health.ai.queue";
    public static final String AI_DLQ = "health.ai.dlq";

    private QueueNames() {
    }
}
