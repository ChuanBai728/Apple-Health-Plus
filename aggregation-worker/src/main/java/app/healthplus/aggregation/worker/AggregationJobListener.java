package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.aggregation.service.AggregationPipelineService;
import app.healthplus.messaging.AggregateJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 聚合任务消息监听器 -- 从 RabbitMQ 聚合队列消费消息，委托 {@link AggregationPipelineService} 执行聚合。
 *
 * <p>处理流程：接收消息 -> 调用聚合管道 -> 记录耗时。
 * 异常时记录错误日志并重新抛出，由 Spring AMQP 的重试机制决定是否进入死信队列。</p>
 *
 * @see QueueNames#AGGREGATE_QUEUE  监听的队列名
 * @see AggregationPipelineService  实际执行聚合的服务
 */
@Component
public class AggregationJobListener {

    private static final Logger log = LoggerFactory.getLogger(AggregationJobListener.class);

    private final AggregationPipelineService aggregationPipelineService;

    public AggregationJobListener(AggregationPipelineService aggregationPipelineService) {
        this.aggregationPipelineService = aggregationPipelineService;
    }

    /**
     * 处理聚合任务消息。
     * 记录处理耗时，成功时输出完成日志，失败时输出错误日志并重新抛出异常。
     *
     * @param message 聚合任务消息，包含 uploadId、userId 等上下文
     */
    @RabbitListener(queues = QueueNames.AGGREGATE_QUEUE)
    public void handle(AggregateJobMessage message) {
        log.info("Received aggregate job: uploadId={}", message.uploadId());
        long start = System.currentTimeMillis();
        try {
            aggregationPipelineService.process(message);
            log.info("Aggregate job completed: uploadId={}, duration={}ms",
                    message.uploadId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Aggregate job failed: uploadId={}, error={}",
                    message.uploadId(), e.getMessage(), e);
            throw e;
        }
    }
}
