package app.healthplus.parser.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 消息序列化配置。
 * <p>
 * 将消息体序列化方式从 JDK 默认的 {@code SimpleMessageConverter}（二进制）替换为
 * Jackson JSON 格式，使 {@link app.healthplus.messaging.ParseJobMessage} 等 record 类型
 * 能自动序列化/反序列化，无需手动转换字节数组。
 */
@Configuration
public class MessagingConfig {

    /** 注册 Jackson JSON 消息转换器，替代默认的二进制序列化 */
    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
