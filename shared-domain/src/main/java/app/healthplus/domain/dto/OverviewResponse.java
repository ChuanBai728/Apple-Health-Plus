package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 健康概览页响应 DTO。
 * <p>用于返回用户健康数据的总览信息，包括报告状态、数据覆盖范围、指标卡片和摘要标题。</p>
 *
 * @param reportId 报告唯一标识
 * @param status   报告状态（如 COMPLETED、PROCESSING）
 * @param coverage 数据覆盖范围信息（记录数、指标数、时间跨度等）
 * @param cards    各健康指标的概览卡片列表
 * @param headline AI 生成的健康摘要标题
 */
public record OverviewResponse(
        UUID reportId,
        String status,
        Coverage coverage,
        List<OverviewCard> cards,
        String headline
) {
    /**
     * 数据覆盖范围信息。
     *
     * @param totalRecords 总健康记录数
     * @param metricCount  涉及的指标种类数
     * @param sourceCount  数据来源设备/应用数
     * @param startDate    数据起始时间
     * @param endDate      数据截止时间
     */
    public record Coverage(
            long totalRecords,
            int metricCount,
            int sourceCount,
            Instant startDate,
            Instant endDate
    ) {}
}
