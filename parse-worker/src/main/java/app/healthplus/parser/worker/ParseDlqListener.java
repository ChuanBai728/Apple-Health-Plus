package app.healthplus.parser.worker;

import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.parser.config.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParseDlqListener {

    private static final Logger log = LoggerFactory.getLogger(ParseDlqListener.class);

    @RabbitListener(queues = QueueNames.PARSE_DLQ)
    public void handleFailedMessage(ParseJobMessage message) {
        log.error("=== Parse job failed (DLQ) ===");
        log.error("uploadId: {}", message.uploadId());
        log.error("userId: {}", message.userId());
        log.error("storageKey: {}", message.storageKey());
        log.error("messageId: {}", message.messageId());
        log.error("createdAt: {}", message.createdAt());
    }
}
