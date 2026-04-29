package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.messaging.AggregateJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

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
