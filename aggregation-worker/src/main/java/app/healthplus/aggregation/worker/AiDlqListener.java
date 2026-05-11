package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.messaging.AiAnalysisCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 分析死信队列监听器 -- 消费进入死信队列的 AI 分析失败消息，记录错误日志用于排查。
 *
 * <p>当 AiJobListener 处理消息多次重试仍失败后，
 * 消息会被路由到死信队列（health.ai.dlq），本监听器捕获并记录详细错误信息。</p>
 */
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
