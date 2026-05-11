package app.healthplus.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * backend-api 模块的 Spring Boot 启动类。
 * <p>
 * 职责：作为 REST API 服务的入口，启动内嵌 Tomcat 服务器，
 * 加载所有 Controller、Service、Repository 等组件。
 * </p>
 * <p>
 * 配置说明：
 * <ul>
 *   <li>{@code scanBasePackages = "app.healthplus"} — 扫描所有模块的组件（含 shared-domain 等）</li>
 *   <li>{@code @EntityScan} — 指定 JPA 实体所在的包（位于 shared-domain 模块）</li>
 *   <li>{@code @EnableJpaRepositories} — 指定本模块的 Repository 接口所在包</li>
 * </ul>
 * </p>
 */
@SpringBootApplication(scanBasePackages = "app.healthplus")
@EntityScan(basePackages = "app.healthplus.domain")
@EnableJpaRepositories(basePackages = "app.healthplus.api.repository")
public class BackendApiApplication {

    /**
     * 应用程序主入口。
     *
     * @param args 命令行参数（可通过 --server.port 等覆盖 application.yml 配置）
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApiApplication.class, args);
    }
}
