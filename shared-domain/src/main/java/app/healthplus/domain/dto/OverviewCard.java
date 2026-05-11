package app.healthplus.domain.dto;

import java.util.List;

/**
 * 健康概览指标卡片 DTO。
 * <p>表示概览页中的一个指标卡片，包含最新值、趋势和近期数据点。</p>
 *
 * @param metricKey    指标唯一标识键（如 "heart_rate"、"steps"）
 * @param label        指标显示名称（如 "静息心率"、"每日步数"）
 * @param latest       最新指标值
 * @param unit         指标单位（如 "bpm"、"步"、"kg"）
 * @param trend30d     近 30 天趋势变化量（正值表示上升）
 * @param anomaly      当前是否存在异常
 * @param recentPoints 近期数据点列表，用于迷你折线图展示
 */
public record OverviewCard(
        String metricKey,
        String label,
        Double latest,
        String unit,
        Double trend30d,
        boolean anomaly,
        List<MetricPoint> recentPoints
) {
}
