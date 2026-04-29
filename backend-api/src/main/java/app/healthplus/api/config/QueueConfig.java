package app.healthplus.api.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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

    @Bean
    Queue aggregateQueue() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AGGREGATE_DLQ)
                .build();
    }

    @Bean
    Queue aggregateDlq() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_DLQ).build();
    }

    @Bean
    Queue aiQueue() {
        return QueueBuilder.durable(QueueNames.AI_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AI_DLQ)
                .build();
    }

    @Bean
    Queue aiDlq() {
        return QueueBuilder.durable(QueueNames.AI_DLQ).build();
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
