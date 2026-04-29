package app.healthplus.parser.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueueConfig {

    @Bean
    Queue parseQueue() {
        return QueueBuilder.durable(QueueNames.PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.PARSE_DLQ)
                .build();
    }

    @Bean
    Queue parseDlq() {
        return QueueBuilder.durable(QueueNames.PARSE_DLQ).build();
    }
}
