package app.healthplus.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 解析服务（parse-worker）启动入口。
 * <p>
 * 职责：从 RabbitMQ 消费 {@code health.parse.queue} 消息，
 * 读取用户上传的 Apple Health export.zip，使用 StAX 流式解析 XML，
 * 通过 PostgreSQL COPY 协议批量写入 health_records 表。
 * <p>
 * scanBasePackages 设为 "app.healthplus" 以扫描 shared-domain、shared-messaging 等共享模块中的组件。
 */
@SpringBootApplication(scanBasePackages = "app.healthplus")
public class ParseWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParseWorkerApplication.class, args);
    }
}
