package app.healthplus.aggregation.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息序列化配置。
 * 使用 Jackson2JsonMessageConverter 将消息体以 JSON 格式序列化/反序列化，
 * 使 AggregateJobMessage、AiAnalysisCommand 等 record 类型可直接作为消息载体。
 */
@Configuration
public class MessagingConfig {

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
