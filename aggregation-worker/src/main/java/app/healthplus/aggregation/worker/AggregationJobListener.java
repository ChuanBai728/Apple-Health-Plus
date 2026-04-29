package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.aggregation.service.AggregationPipelineService;
import app.healthplus.messaging.AggregateJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AggregationJobListener {

    private static final Logger log = LoggerFactory.getLogger(AggregationJobListener.class);

    private final AggregationPipelineService aggregationPipelineService;

    public AggregationJobListener(AggregationPipelineService aggregationPipelineService) {
        this.aggregationPipelineService = aggregationPipelineService;
    }

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
