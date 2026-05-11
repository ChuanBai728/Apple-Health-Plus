package app.healthplus.parser.config;

/**
 * RabbitMQ 队列名称常量定义。
 * <p>
 * 集中管理所有队列名称，避免硬编码分散在各处。
 * 三种队列分别对应：
 * <ul>
 *   <li>PARSE_QUEUE — 解析任务主队列，backend-api 发送、parse-worker 消费</li>
 *   <li>PARSE_DLQ — 解析任务死信队列，消费失败的消息进入此队列供人工排查</li>
 *   <li>AGGREGATE_QUEUE — 聚合任务队列，解析完成后通知 aggregation-worker</li>
 * </ul>
 */
public final class QueueNames {
    public static final String PARSE_QUEUE = "health.parse.queue";
    public static final String PARSE_DLQ = "health.parse.dlq";
    public static final String AGGREGATE_QUEUE = "health.aggregate.queue";

    /** 私有构造，防止实例化工具类 */
    private QueueNames() {
    }
}
