package app.healthplus.aggregation.config;

import app.healthplus.ai.HealthKnowledgeBase;
import java.util.List;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * RabbitMQ 队列与 AI 知识库的 Spring 配置类。
 *
 * <h3>队列架构</h3>
 * <ul>
 *   <li>聚合主队列 (health.aggregate.queue) + 聚合死信队列 (health.aggregate.dlq)</li>
 *   <li>AI 分析主队列 (health.ai.queue) + AI 死信队列 (health.ai.dlq)</li>
 * </ul>
 *
 * <p>主队列配置了死信交换机（x-dead-letter-exchange），
 * 当消息处理失败并被 reject 时，自动路由到对应的 DLQ，避免消息丢失。</p>
 *
 * <h3>知识库初始化</h3>
 * <p>启动时从 health_knowledge 表加载所有知识条目，构建 {@link HealthKnowledgeBase} Bean，
 * 供 {@link app.healthplus.aggregation.ai.AiOrchestratorService} 进行 RAG 检索。</p>
 */
@Configuration
public class QueueConfig {

    /**
     * 初始化健康知识库 Bean。
     * 从 PostgreSQL 的 health_knowledge 表加载所有知识条目，
     * 构建内存知识库用于后续的 RAG 语义检索。
     * 加载失败时返回空知识库，不影响应用启动。
     */
    @Bean
    HealthKnowledgeBase healthKnowledgeBase(@Autowired DataSource dataSource) {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        List<HealthKnowledgeBase.Entry> entries = loadKnowledgeEntries(jdbc);
        return new HealthKnowledgeBase(entries);
    }

    /**
     * 从数据库加载健康知识条目。
     * 每条记录包含 id、分类、标题、内容和关键词（逗号分隔）。
     * 查询失败时返回空列表，保证应用可正常启动。
     */
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

    /** 聚合主队列：持久化队列，配置死信路由到 aggregateDlq */
    @Bean
    Queue aggregateQueue() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AGGREGATE_DLQ)
                .build();
    }

    /** 聚合死信队列：接收聚合失败的消息，供 AggregationDlqListener 监控 */
    @Bean
    Queue aggregateDlq() {
        return QueueBuilder.durable(QueueNames.AGGREGATE_DLQ).build();
    }

    /** AI 分析主队列：持久化队列，配置死信路由到 aiDlq */
    @Bean
    Queue aiQueue() {
        return QueueBuilder.durable(QueueNames.AI_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", QueueNames.AI_DLQ)
                .build();
    }

    /** AI 死信队列：接收 AI 分析失败的消息，供 AiDlqListener 监控 */
    @Bean
    Queue aiDlq() {
        return QueueBuilder.durable(QueueNames.AI_DLQ).build();
    }
}
