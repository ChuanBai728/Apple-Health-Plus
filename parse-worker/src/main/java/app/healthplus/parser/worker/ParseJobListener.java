package app.healthplus.parser.worker;

import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.parser.config.QueueNames;
import app.healthplus.parser.service.ParsePipelineService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 解析任务消息监听器（RabbitMQ 消费者）。
 * <p>
 * 监听 {@code health.parse.queue} 队列，收到 {@link ParseJobMessage} 后
 * 委托 {@link ParsePipelineService} 执行完整的 XML 解析流程。
 * <p>
 * 如果处理过程中抛出异常，Spring AMQP 默认会 nack 消息并重新入队；
 * 达到最大重试次数后消息会被路由到死信队列（参见 {@link QueueConfig}）。
 */
@Component
public class ParseJobListener {

    private final ParsePipelineService parsePipelineService;

    public ParseJobListener(ParsePipelineService parsePipelineService) {
        this.parsePipelineService = parsePipelineService;
    }

    /**
     * 消费解析任务消息，触发 XML 解析流水线。
     *
     * @param message 解析任务消息，包含 uploadId、userId、storageKey 等信息
     * @throws Exception 解析失败时抛出，触发消息 nack 和重试
     */
    @RabbitListener(queues = QueueNames.PARSE_QUEUE)
    public void handle(ParseJobMessage message) throws Exception {
        parsePipelineService.process(message);
    }
}
