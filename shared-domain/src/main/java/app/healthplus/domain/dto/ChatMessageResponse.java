package app.healthplus.domain.dto;

import java.util.List;

/**
 * AI 聊天消息响应 DTO。
 * <p>用于返回 AI 对用户健康问题的结构化回答。</p>
 *
 * @param intent      AI 识别的用户意图（如 "SLEEP_ANALYSIS"、"HEART_CARDIOVASCULAR"）
 * @param conclusion  AI 综合分析结论
 * @param evidence    引用的健康数据证据列表
 * @param advice      AI 给出的健康建议列表
 * @param disclaimer  免责声明文本
 */
public record ChatMessageResponse(
        String intent,
        String conclusion,
        List<String> evidence,
        List<String> advice,
        String disclaimer
) {
}
