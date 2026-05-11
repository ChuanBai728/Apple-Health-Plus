package app.healthplus.aggregation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 聚合服务启动入口。
 * scanBasePackages = "app.healthplus" 扫描共享模块中的组件（如 HealthKnowledgeBase、消息体 record 等）。
 */
@SpringBootApplication(scanBasePackages = "app.healthplus")
public class AggregationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AggregationWorkerApplication.class, args);
    }
}
