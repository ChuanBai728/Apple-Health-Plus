package app.healthplus.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * AI 聊天消息请求 DTO。
 * <p>用户向 AI 助手发送健康问题时的请求体。</p>
 *
 * @param question 用户的健康相关问题（不可为空）
 * @param uploadId 关联的健康数据上传记录 ID，AI 将基于该数据进行分析
 */
public record ChatMessageRequest(
        @NotBlank String question,
        UUID uploadId
) {
}
