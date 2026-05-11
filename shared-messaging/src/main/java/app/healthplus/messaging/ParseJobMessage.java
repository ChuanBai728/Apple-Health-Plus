package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * XML 解析任务消息 DTO。
 * <p>通过 RabbitMQ 发送至 parse-worker，触发 Apple Health XML 文件的解析流程。
 * 解析完成后将健康记录批量写入数据库。</p>
 *
 * @param uploadId    上传记录唯一标识
 * @param userId      所属用户 ID
 * @param storageKey  文件在存储系统中的唯一键（用于从 MinIO/S3 读取文件）
 * @param messageId   消息唯一标识（用于幂等处理和去重）
 * @param createdAt   消息创建时间
 */
public record ParseJobMessage(
        UUID uploadId,
        UUID userId,
        String storageKey,
        String messageId,
        Instant createdAt
) implements Serializable {
}
