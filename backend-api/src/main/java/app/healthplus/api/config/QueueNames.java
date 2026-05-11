package app.healthplus.api.config;

/**
 * RabbitMQ 队列名称常量类。
 * <p>
 * 集中管理所有队列和死信队列（DLQ）的名称，避免硬编码分散在各处。
 * 三组队列分别对应三个 Worker 的任务处理：
 * <ul>
 *   <li>parse —— XML 解析（parse-worker）</li>
 *   <li>aggregate —— 数据聚合（aggregation-worker）</li>
 *   <li>ai —— AI 分析（backend-api 内部）</li>
 * </ul>
 * 每个主队列都有对应的 DLQ，用于存放消费失败的消息。
 */
public final class QueueNames {
    public static final String PARSE_QUEUE = "health.parse.queue";
    public static final String PARSE_DLQ = "health.parse.dlq";
    public static final String AGGREGATE_QUEUE = "health.aggregate.queue";
    public static final String AGGREGATE_DLQ = "health.aggregate.dlq";
    public static final String AI_QUEUE = "health.ai.queue";
    public static final String AI_DLQ = "health.ai.dlq";

    private QueueNames() {
    }
}
