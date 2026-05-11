package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 数据聚合任务消息 DTO。
 * <p>通过 RabbitMQ 发送至 aggregation-worker，触发日/周/月维度的健康指标聚合计算。
 * 通常在 XML 解析完成后自动触发。</p>
 *
 * @param uploadId    上传记录唯一标识
 * @param userId      所属用户 ID
 * @param messageId   消息唯一标识（用于幂等处理和去重）
 * @param triggeredBy 触发来源（如 "parse_completed" 表示解析完成后触发）
 * @param createdAt   消息创建时间
 */
public record AggregateJobMessage(
        UUID uploadId,
        UUID userId,
        String messageId,
        String triggeredBy,
        Instant createdAt
) implements Serializable {
}
