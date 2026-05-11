package app.healthplus.parser.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列声明配置。
 * <p>
 * 定义解析任务的主队列和死信队列（DLQ）。主队列配置了死信交换机，
 * 当消息消费失败（reject/nack 且不重新入队）时，消息会被路由到 DLQ，
 * 实现"失败隔离"——避免毒消息阻塞正常处理。
 */
@Configuration
public class QueueConfig {

    /**
     * 解析任务主队列：持久化、绑定死信策略。
     * x-dead-letter-exchange 设为空字符串表示使用默认直连交换机，
     * routing-key 指向 PARSE_DLQ，消费失败的消息自动转入死信队列。
     */
    @Bean
    Queue parseQueue() {
        return QueueBuilder.durable(QueueNames.PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.PARSE_DLQ)
                .build();
    }

    /** 死信队列：存放消费失败的消息，供 DLQ Listener 记录日志和人工排查 */
    @Bean
    Queue parseDlq() {
        return QueueBuilder.durable(QueueNames.PARSE_DLQ).build();
    }
}
