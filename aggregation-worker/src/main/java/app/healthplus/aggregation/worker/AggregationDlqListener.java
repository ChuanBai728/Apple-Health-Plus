package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.messaging.AggregateJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 聚合死信队列监听器 -- 消费进入死信队列的聚合失败消息，记录错误日志用于排查。
 *
 * <p>当 AggregationJobListener 处理消息多次重试仍失败后，
 * 消息会被路由到死信队列（health.aggregate.dlq），本监听器捕获并记录详细错误信息。</p>
 */
@Component
public class AggregationDlqListener {

    private static final Logger log = LoggerFactory.getLogger(AggregationDlqListener.class);

    @RabbitListener(queues = QueueNames.AGGREGATE_DLQ)
    public void handleFailedMessage(AggregateJobMessage message) {
        log.error("=== Aggregation job failed (DLQ) ===");
        log.error("uploadId: {}", message.uploadId());
        log.error("userId: {}", message.userId());
        log.error("messageId: {}", message.messageId());
        log.error("triggeredBy: {}", message.triggeredBy());
        log.error("createdAt: {}", message.createdAt());
    }
}
