package app.healthplus.parser.worker;

import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.parser.config.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列（DLQ）监听器。
 * <p>
 * 监听 {@code health.parse.dlq} 队列，记录所有消费失败的解析任务详情。
 * 这些消息已经经过重试仍然失败，代表可能存在数据损坏、格式异常等问题，
 * 记录日志后可配合告警系统通知运维人员介入排查。
 */
@Component
public class ParseDlqListener {

    private static final Logger log = LoggerFactory.getLogger(ParseDlqListener.class);

    /**
     * 接收死信消息并记录错误日志。
     *
     * @param message 多次消费失败后被路由到 DLQ 的解析任务消息
     */
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
