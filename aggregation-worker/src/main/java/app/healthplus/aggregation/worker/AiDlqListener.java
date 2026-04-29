package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.messaging.AiAnalysisCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiDlqListener {

    private static final Logger log = LoggerFactory.getLogger(AiDlqListener.class);

    @RabbitListener(queues = QueueNames.AI_DLQ)
    public void handleFailedMessage(AiAnalysisCommand command) {
        log.error("=== AI analysis failed (DLQ) ===");
        log.error("uploadId: {}", command.uploadId());
        log.error("question: {}", command.question());
        log.error("sessionId: {}", command.sessionId());
        log.error("messageId: {}", command.messageId());
        log.error("createdAt: {}", command.createdAt());
    }
}
