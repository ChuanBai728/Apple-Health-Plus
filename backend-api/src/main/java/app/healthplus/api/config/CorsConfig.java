package app.healthplus.api.config;

import app.healthplus.ai.HealthKnowledgeBase;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

@Configuration
public class CorsConfig {

    @Bean
    HealthKnowledgeBase healthKnowledgeBase(@Autowired DataSource dataSource) {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        List<HealthKnowledgeBase.Entry> entries = loadKnowledgeEntries(jdbc);
        return new HealthKnowledgeBase(entries);
    }

    private List<HealthKnowledgeBase.Entry> loadKnowledgeEntries(
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        try {
            return jdbc.query(
                "SELECT id, category, title, content, keywords FROM health_knowledge ORDER BY category, id",
                (rs, i) -> new HealthKnowledgeBase.Entry(
                    rs.getString("id"), rs.getString("category"),
                    rs.getString("title"), rs.getString("content"),
                    List.of(rs.getString("keywords").split(",\\s*")))
            );
        } catch (Exception e) {
            System.out.println("[HealthKnowledgeBase] DB unavailable, using defaults: " + e.getMessage());
            return List.of();
        }
    }

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:3000",
                                "http://127.0.0.1:3000"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
