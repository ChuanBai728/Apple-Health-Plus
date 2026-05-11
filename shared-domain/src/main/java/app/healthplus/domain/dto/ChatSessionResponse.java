package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 聊天会话详情响应 DTO。
 * <p>用于返回一次完整的聊天会话信息，包括会话元数据和历史消息列表。</p>
 *
 * @param sessionId  会话唯一标识
 * @param uploadId   关联的健康数据上传记录 ID
 * @param title      会话标题（通常由首条用户消息生成）
 * @param createdAt  会话创建时间
 * @param messages   会话中的历史消息列表
 */
public record ChatSessionResponse(
        UUID sessionId,
        UUID uploadId,
        String title,
        Instant createdAt,
        List<ChatHistoryMessage> messages
) {
    /**
     * 聊天历史消息记录。
     *
     * @param role       消息角色，如 "user"（用户）或 "assistant"（AI 助手）
     * @param content    消息正文内容
     * @param intent     AI 识别的用户意图（仅 AI 回复时有值）
     * @param evidence   AI 回复引用的健康数据证据列表
     * @param advice     AI 给出的健康建议列表
     * @param disclaimer 免责声明文本
     * @param createdAt  消息创建时间
     */
    public record ChatHistoryMessage(
            String role,
            String content,
            String intent,
            List<String> evidence,
            List<String> advice,
            String disclaimer,
            Instant createdAt
    ) {
    }
}
