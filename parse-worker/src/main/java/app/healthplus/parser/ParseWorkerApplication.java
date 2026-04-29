package app.healthplus.parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "app.healthplus")
public class ParseWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParseWorkerApplication.class, args);
    }
}
