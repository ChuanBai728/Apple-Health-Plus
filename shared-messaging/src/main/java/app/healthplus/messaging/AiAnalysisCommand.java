package app.healthplus.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * AI 分析命令消息 DTO。
 * <p>通过 RabbitMQ 异步处理用户的 AI 健康分析请求。
 * 当用户通过聊天界面提交健康问题时，系统将此消息发送至后端进行异步 AI 分析，
 * 避免长时间阻塞 HTTP 请求。</p>
 *
 * @param sessionId   聊天会话唯一标识
 * @param uploadId    关联的健康数据上传记录 ID（AI 将基于该数据进行分析）
 * @param userId      所属用户 ID
 * @param question    用户提出的健康问题
 * @param messageId   消息唯一标识（用于幂等处理和去重）
 * @param createdAt   消息创建时间
 */
public record AiAnalysisCommand(
        UUID sessionId,
        UUID uploadId,
        UUID userId,
        String question,
        String messageId,
        Instant createdAt
) implements Serializable {
}
