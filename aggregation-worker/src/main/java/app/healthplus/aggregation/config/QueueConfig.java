package app.healthplus.aggregation.config;

import app.healthplus.ai.HealthKnowledgeBase;
import java.util.List;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class QueueConfig {

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
            return List.of();
        }
    }

    @Bean
    Queue aggregateQueue() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AGGREGATE_DLQ)
                .build();
    }

    @Bean
    Queue aggregateDlq() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_DLQ).build();
    }

    @Bean
    Queue aiQueue() {
        return QueueBuilder.durable(QueueNames.AI_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AI_DLQ)
                .build();
    }

    @Bean
    Queue aiDlq() {
        return QueueBuilder.durable(QueueNames.AI_DLQ).build();
    }
}
