package app.healthplus.parser.core;

import java.time.Instant;

/**
 * 解析后的健康记录 DTO，由 {@link AppleHealthParser} 从原始 XML 属性转换而来。
 *
 * <p>字段说明：
 * <ul>
 *   <li>recordType — Apple 原始类型标识（如 HKQuantityTypeIdentifierHeartRate）</li>
 *   <li>metricKey — 规范化后的指标名（如 heart_rate），由映射表转换</li>
 *   <li>categoryKey — 指标类别（heart/sleep/activity/body/vital），用于聚合分组</li>
 *   <li>sourceName — 数据来源设备名（如 Apple Watch Series 8）</li>
 *   <li>valueNumeric — 数值型值（心率 72.0、步数 8500），睡眠记录为小时数</li>
 *   <li>valueText — 文本型值（如睡眠分类 HKCategoryValueSleepAnalysisAsleepCore）</li>
 *   <li>unit — 计量单位（count/min/hr/kg/%），用于前端展示</li>
 *   <li>startAt/endAt — 记录的时间范围（瞬时事件 startAt == endAt）</li>
 *   <li>rawPayloadJson — 原始 XML 属性的 JSON 表示，存入 JSONB 列便于调试和扩展</li>
 * </ul>
 */
public record ParsedHealthRecord(
        String recordType,
        String metricKey,
        String categoryKey,
        String sourceName,
        Double valueNumeric,
        String valueText,
        String unit,
        Instant startAt,
        Instant endAt,
        String rawPayloadJson
) {
}
