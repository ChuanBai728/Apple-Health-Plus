package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.ai.AiOrchestratorService;
import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.ai.AiAnswer;
import app.healthplus.messaging.AiAnalysisCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiJobListener {

    private static final Logger log = LoggerFactory.getLogger(AiJobListener.class);

    private final AiOrchestratorService aiOrchestratorService;

    public AiJobListener(AiOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    @RabbitListener(queues = QueueNames.AI_QUEUE)
    public void handle(AiAnalysisCommand command) {
        log.info("Received AI analysis job: uploadId={}", command.uploadId());
        long start = System.currentTimeMillis();
        try {
            AiAnswer answer = aiOrchestratorService.analyzeUpload(
                    command.uploadId().toString(),
                    command.question()
            );
            aiOrchestratorService.saveSummary(command.uploadId(), answer);
            log.info("AI analysis completed: uploadId={}, duration={}ms",
                    command.uploadId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("AI analysis failed: uploadId={}, error={}",
                    command.uploadId(), e.getMessage(), e);
            throw e;
        }
    }
}
