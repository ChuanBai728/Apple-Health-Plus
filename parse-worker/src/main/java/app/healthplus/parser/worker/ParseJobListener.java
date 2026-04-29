package app.healthplus.parser.worker;

import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.parser.config.QueueNames;
import app.healthplus.parser.service.ParsePipelineService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParseJobListener {

    private final ParsePipelineService parsePipelineService;

    public ParseJobListener(ParsePipelineService parsePipelineService) {
        this.parsePipelineService = parsePipelineService;
    }

    @RabbitListener(queues = QueueNames.PARSE_QUEUE)
    public void handle(ParseJobMessage message) throws Exception {
        parsePipelineService.process(message);
    }
}
