package app.healthplus.ai;

import java.util.List;

/**
 * AI 分析结果 DTO。
 * <p>封装 AI 对用户健康问题的结构化回答，包含意图识别、结论、证据、建议和免责声明。</p>
 *
 * @param intent      AI 识别的用户意图（对应 {@link AiIntent} 枚举）
 * @param conclusion  综合分析结论
 * @param evidence    引用的健康数据证据列表
 * @param advice      AI 给出的个性化健康建议列表
 * @param disclaimer  免责声明文本（提示用户本分析不构成医疗诊断）
 */
public record AiAnswer(
        AiIntent intent,
        String conclusion,
        List<String> evidence,
        List<String> advice,
        String disclaimer
) {
}
