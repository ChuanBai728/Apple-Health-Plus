package app.healthplus.ai;

/**
 * AI 用户意图分类枚举。
 * <p>用于对用户健康问题进行意图分类，指导 AI 分析流程和知识检索策略。</p>
 *
 * <ul>
 *   <li>{@link #OVERALL_SUMMARY} — 总体健康概况摘要</li>
 *   <li>{@link #SLEEP_ANALYSIS} — 睡眠质量与模式分析</li>
 *   <li>{@link #RECOVERY_ANALYSIS} — 运动后恢复情况分析</li>
 *   <li>{@link #ACTIVITY_TREND} — 运动与活动趋势分析</li>
 *   <li>{@link #HEART_CARDIOVASCULAR} — 心脏与心血管健康分析</li>
 *   <li>{@link #WEIGHT_CHANGE} — 体重/体成分变化分析</li>
 *   <li>{@link #RISK_SIGNAL} — 健康风险信号识别与预警</li>
 * </ul>
 */
public enum AiIntent {
    OVERALL_SUMMARY,
    SLEEP_ANALYSIS,
    RECOVERY_ANALYSIS,
    ACTIVITY_TREND,
    HEART_CARDIOVASCULAR,
    WEIGHT_CHANGE,
    RISK_SIGNAL
}
