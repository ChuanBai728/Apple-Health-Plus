package app.healthplus.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 分析上下文 DTO。
 * <p>封装 AI 进行健康分析时所需的全部上下文信息，包括用户问题、
 * 数据覆盖时间范围、聚合指标数据和已识别的风险信号。</p>
 *
 * @param question      用户提出的健康问题
 * @param coverageStart 健康数据覆盖的起始日期
 * @param coverageEnd   健康数据覆盖的截止日期
 * @param metrics       聚合后的健康指标数据（key 为指标名，value 为指标值/统计信息）
 * @param riskSignals   系统已识别的潜在健康风险信号列表
 */
public record AiAnalysisContext(
        String question,
        String coverageStart,
        String coverageEnd,
        Map<String, Object> metrics,
        List<String> riskSignals
) {
}
