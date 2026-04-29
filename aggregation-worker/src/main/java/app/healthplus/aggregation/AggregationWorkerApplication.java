package app.healthplus.aggregation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "app.healthplus")
public class AggregationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AggregationWorkerApplication.class, args);
    }
}
