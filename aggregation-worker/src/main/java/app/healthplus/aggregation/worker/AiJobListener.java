package app.healthplus.aggregation.worker;

import app.healthplus.aggregation.ai.AiOrchestratorService;
import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.ai.AiAnswer;
import app.healthplus.messaging.AiAnalysisCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI 分析任务消息监听器 -- 从 RabbitMQ AI 队列消费消息，
 * 委托 {@link AiOrchestratorService} 执行 AI 健康分析并保存摘要。
 *
 * <p>处理流程：接收消息 -> 调用 AI 分析 -> 持久化结果 -> 记录耗时。
 * 异常时记录错误日志并重新抛出，由 Spring AMQP 的重试机制决定是否进入死信队列。</p>
 *
 * @see QueueNames#AI_QUEUE          监听的队列名
 * @see AiOrchestratorService        实际执行 AI 分析的服务
 */
@Component
public class AiJobListener {

    private static final Logger log = LoggerFactory.getLogger(AiJobListener.class);

    private final AiOrchestratorService aiOrchestratorService;

    public AiJobListener(AiOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    /**
     * 处理 AI 分析任务指令。
     * 执行两步操作：1) 调用 AI 分析获取结果；2) 将结果持久化到数据库。
     *
     * @param command AI 分析指令，包含 uploadId、question、sessionId 等
     */
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
