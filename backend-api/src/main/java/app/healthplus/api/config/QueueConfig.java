package app.healthplus.api.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 队列配置。
 * <p>
 * 声明三组业务队列及其对应的死信队列（DLQ）：
 * <ul>
 *   <li><b>parse</b>：XML 解析任务队列，接收上传文件的解析请求</li>
 *   <li><b>aggregate</b>：数据聚合任务队列，接收日/周/月指标计算请求</li>
 *   <li><b>ai</b>：AI 分析任务队列，接收健康报告生成请求</li>
 * </ul>
 * <p>
 * 每个主队列都配置了死信路由：当消息消费失败或被 reject 时，
 * 自动路由到对应的 DLQ，避免消息丢失，便于后续人工排查或重试。
 */
@Configuration
public class QueueConfig {

    /**
     * XML 解析任务队列。
     * 消费失败的消息将被路由到 parseDlq。
     */
    @Bean
    Queue parseQueue() {
        return QueueBuilder.durable(QueueNames.PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.PARSE_DLQ)
                .build();
    }

    /** 解析任务死信队列，存放 parseQueue 中消费失败的消息 */
    @Bean
    Queue parseDlq() {
        return QueueBuilder.durable(QueueNames.PARSE_DLQ).build();
    }

    /** 数据聚合任务队列，消费失败的消息将被路由到 aggregateDlq */
    @Bean
    Queue aggregateQueue() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AGGREGATE_DLQ)
                .build();
    }

    /** 聚合任务死信队列，存放 aggregateQueue 中消费失败的消息 */
    @Bean
    Queue aggregateDlq() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_DLQ).build();
    }

    /** AI 分析任务队列，消费失败的消息将被路由到 aiDlq */
    @Bean
    Queue aiQueue() {
        return QueueBuilder.durable(QueueNames.AI_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AI_DLQ)
                .build();
    }

    /** AI 任务死信队列，存放 aiQueue 中消费失败的消息 */
    @Bean
    Queue aiDlq() {
        return QueueBuilder.durable(QueueNames.AI_DLQ).build();
    }

    /**
     * RabbitMQ 消息转换器。
     * 使用 Jackson JSON 序列化消息体，使 Java 对象可直接作为消息发送和接收，
     * 无需手动处理 byte[] 转换。
     */
    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
