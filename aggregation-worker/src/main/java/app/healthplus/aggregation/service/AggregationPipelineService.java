package app.healthplus.aggregation.service;

import app.healthplus.aggregation.config.QueueNames;
import app.healthplus.messaging.AggregateJobMessage;
import app.healthplus.messaging.AiAnalysisCommand;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聚合管道服务 -- 负责将已解析的健康原始记录（health_records）汇总为
 * 日 / 周 / 月 三级粒度指标，并计算基线均值、趋势变化量和异常标记。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>标记 upload 状态为 AGGREGATING</li>
 *   <li>重建 daily / weekly / monthly 三级聚合表</li>
 *   <li>基于窗口函数计算滚动基线与趋势 delta</li>
 *   <li>标记 upload 状态为 READY</li>
 *   <li>向 AI 队列发送分析指令，触发后续智能摘要</li>
 * </ol>
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>采用"先删后插"策略，保证每次聚合的幂等性（可重入）</li>
 *   <li>聚合与 AI 分析解耦：聚合完成后通过 RabbitMQ 异步触发 AI</li>
 *   <li>异常时标记 upload 为 FAILED 并向上抛出，由消息监听器决定重试 / 死信</li>
 * </ul>
 *
 * @see app.healthplus.aggregation.worker.AggregationJobListener  接收聚合消息并调用本服务
 * @see app.healthplus.aggregation.ai.AiOrchestratorService       接收 AI 指令并执行分析
 */
@Service
public class AggregationPipelineService {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public AggregationPipelineService(JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 聚合管道主入口：执行指标聚合，成功后触发 AI 摘要。
     *
     * @param message 聚合任务消息，包含 uploadId / userId 等上下文
     */
    public void process(AggregateJobMessage message) {
        try {
            doAggregate(message);
            triggerAiSummary(message);
        } catch (Exception e) {
            markFailed(message.uploadId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 执行聚合管道的核心事务方法。
     * 整个过程在一个事务内完成，任何一步失败都会回滚。
     *
     * @param message 聚合任务消息
     */
    @Transactional
    private void doAggregate(AggregateJobMessage message) {
        markAggregating(message.uploadId());
        rebuildDailyMetrics(message.uploadId());
        rebuildWeeklyMetrics(message.uploadId());
        rebuildMonthlyMetrics(message.uploadId());
        computeBaselinesAndTrends(message.uploadId());
        markReady(message.uploadId());
    }

    /**
     * 聚合成功后向 AI 队列发送分析指令，触发异步 AI 健康摘要。
     * sessionId 设为 null，表示自动生成摘要（非用户主动发起的对话）。
     */
    private void triggerAiSummary(AggregateJobMessage message) {
        rabbitTemplate.convertAndSend(
                QueueNames.AI_QUEUE,
                new AiAnalysisCommand(
                        null,           // sessionId: null = auto-generated summary
                        message.uploadId(),
                        message.userId(),
                        "分析我的整体健康状态并给出建议",
                        message.messageId(),
                        Instant.now()
                )
        );
    }

    /**
     * 将 upload 状态标记为 AGGREGATING，表示正在处理中。
     * 前端可通过此状态展示进度条。
     */
    private void markAggregating(UUID uploadId) {
        jdbcTemplate.update("update uploads set status = ? where id = ?", "AGGREGATING", uploadId);
    }

    /**
     * 重建日级聚合指标（health_metric_daily）。
     * 采用"先删后插"策略：删除该 upload 已有的日聚合数据，再从 health_records 重新聚合。
     * 聚合维度：按 metric_key + 日期分组，计算 avg / min / max / sum / count。
     */
    private void rebuildDailyMetrics(UUID uploadId) {
        jdbcTemplate.update("delete from health_metric_daily where upload_id = ?", uploadId);
        jdbcTemplate.update(
                """
                insert into health_metric_daily
                    (user_id, upload_id, metric_key, date, value_avg, value_min, value_max, value_sum, sample_count, created_at)
                select
                    user_id,
                    upload_id,
                    metric_key,
                    cast(start_at as date) as date,
                    avg(value_numeric),
                    min(value_numeric),
                    max(value_numeric),
                    sum(value_numeric),
                    count(*),
                    ?
                from health_records
                where upload_id = ?
                  and value_numeric is not null
                group by user_id, upload_id, metric_key, cast(start_at as date)
                """,
                Timestamp.from(Instant.now()),
                uploadId
        );
    }

    /**
     * 重建周级聚合指标（health_metric_weekly）。
     * 基于日聚合数据，按 ISO 周（date_trunc('week')）再次聚合。
     * 输入是日聚合结果而非原始记录，避免重复扫描大表。
     */
    private void rebuildWeeklyMetrics(UUID uploadId) {
        jdbcTemplate.update("delete from health_metric_weekly where upload_id = ?", uploadId);
        jdbcTemplate.update(
                """
                insert into health_metric_weekly
                    (user_id, upload_id, metric_key, week_start, value_avg, value_min, value_max, value_sum, sample_count, created_at)
                select
                    user_id,
                    upload_id,
                    metric_key,
                    date_trunc('week', date)::date as week_start,
                    avg(value_avg),
                    min(value_min),
                    max(value_max),
                    sum(value_sum),
                    sum(sample_count),
                    ?
                from health_metric_daily
                where upload_id = ?
                group by user_id, upload_id, metric_key, date_trunc('week', date)::date
                """,
                Timestamp.from(Instant.now()),
                uploadId
        );
    }

    /**
     * 重建月级聚合指标（health_metric_monthly）。
     * 基于日聚合数据，按月（date_trunc('month')）再次聚合。
     */
    private void rebuildMonthlyMetrics(UUID uploadId) {
        jdbcTemplate.update("delete from health_metric_monthly where upload_id = ?", uploadId);
        jdbcTemplate.update(
                """
                insert into health_metric_monthly
                    (user_id, upload_id, metric_key, month_start, value_avg, value_min, value_max, value_sum, sample_count, created_at)
                select
                    user_id,
                    upload_id,
                    metric_key,
                    date_trunc('month', date)::date as month_start,
                    avg(value_avg),
                    min(value_min),
                    max(value_max),
                    sum(value_sum),
                    sum(sample_count),
                    ?
                from health_metric_daily
                where upload_id = ?
                group by user_id, upload_id, metric_key, date_trunc('month', date)::date
                """,
                Timestamp.from(Instant.now()),
                uploadId
        );
    }

    /**
     * 为三级聚合表计算基线均值、趋势变化量和异常标记。
     * 使用 PostgreSQL 窗口函数实现高效的滚动计算，避免子查询。
     *
     * <p><b>计算规则：</b></p>
     * <ul>
     *   <li>日级：30日滚动均值基线 + 7日/30日趋势 delta + 异常标记（>2.5σ）</li>
     *   <li>周级：4周滚动均值基线 + 1周/4周趋势 delta</li>
     *   <li>月级：12月滚动均值基线 + 1月趋势 delta</li>
     * </ul>
     *
     * <p><b>异常检测原理：</b>当某天的值偏离30日滚动均值超过2.5倍标准差时标记为异常。</p>
     */
    private void computeBaselinesAndTrends(UUID uploadId) {
        // Daily: 30-day rolling average baseline, 7-day and 30-day trend deltas via LAG
        jdbcTemplate.update(
                """
                update health_metric_daily d set
                    baseline_avg_30d = sub.rolling_avg_30d,
                    trend_delta_7d = d.value_avg - sub.lag_7d,
                    trend_delta_30d = d.value_avg - sub.lag_30d,
                    anomaly_flag = case
                        when sub.rolling_stddev_30d > 0
                             and abs(d.value_avg - sub.rolling_avg_30d) > 2.5 * sub.rolling_stddev_30d then true
                        else false
                    end
                from (
                    select upload_id, metric_key, date,
                           avg(value_avg) over (
                               partition by upload_id, metric_key
                               order by date
                               rows between 30 preceding and 1 preceding
                           ) as rolling_avg_30d,
                           coalesce(stddev(value_avg) over (
                               partition by upload_id, metric_key
                               order by date
                               rows between 30 preceding and 1 preceding
                           ), 0) as rolling_stddev_30d,
                           lag(value_avg, 7) over (
                               partition by upload_id, metric_key
                               order by date
                           ) as lag_7d,
                           lag(value_avg, 30) over (
                               partition by upload_id, metric_key
                               order by date
                           ) as lag_30d
                    from health_metric_daily
                    where upload_id = ?
                ) sub
                where d.upload_id = sub.upload_id
                  and d.metric_key = sub.metric_key
                  and d.date = sub.date
                """,
                uploadId
        );

        // Weekly: 4-week rolling average baseline, 1-week and 4-week trend deltas
        jdbcTemplate.update(
                """
                update health_metric_weekly w set
                    baseline_avg_30d = sub.rolling_avg_4w,
                    trend_delta_7d = w.value_avg - sub.lag_1w,
                    trend_delta_30d = w.value_avg - sub.lag_4w
                from (
                    select upload_id, metric_key, week_start,
                           avg(value_avg) over (
                               partition by upload_id, metric_key
                               order by week_start
                               rows between 4 preceding and 1 preceding
                           ) as rolling_avg_4w,
                           lag(value_avg, 1) over (
                               partition by upload_id, metric_key
                               order by week_start
                           ) as lag_1w,
                           lag(value_avg, 4) over (
                               partition by upload_id, metric_key
                               order by week_start
                           ) as lag_4w
                    from health_metric_weekly
                    where upload_id = ?
                ) sub
                where w.upload_id = sub.upload_id
                  and w.metric_key = sub.metric_key
                  and w.week_start = sub.week_start
                """,
                uploadId
        );

        // Monthly: 12-month rolling average baseline, 1-month trend delta
        jdbcTemplate.update(
                """
                update health_metric_monthly m set
                    baseline_avg_30d = sub.rolling_avg_12m,
                    trend_delta_30d = m.value_avg - sub.lag_1m
                from (
                    select upload_id, metric_key, month_start,
                           avg(value_avg) over (
                               partition by upload_id, metric_key
                               order by month_start
                               rows between 12 preceding and 1 preceding
                           ) as rolling_avg_12m,
                           lag(value_avg, 1) over (
                               partition by upload_id, metric_key
                               order by month_start
                           ) as lag_1m
                    from health_metric_monthly
                    where upload_id = ?
                ) sub
                where m.upload_id = sub.upload_id
                  and m.metric_key = sub.metric_key
                  and m.month_start = sub.month_start
                """,
                uploadId
        );
    }

    /**
     * 聚合完成：将 upload 状态标记为 READY，记录完成时间，清除之前的错误信息。
     */
    private void markReady(UUID uploadId) {
        jdbcTemplate.update(
                "update uploads set status = ?, finished_at = ?, last_error = null where id = ?",
                "READY",
                Timestamp.from(Instant.now()),
                uploadId
        );
    }

    /**
     * 聚合失败：将 upload 状态标记为 FAILED，记录错误信息和失败时间。
     *
     * @param error 异常消息，null 时使用默认文本
     */
    private void markFailed(UUID uploadId, String error) {
        jdbcTemplate.update(
                "update uploads set status = ?, last_error = ?, finished_at = ? where id = ?",
                "FAILED",
                error == null ? "Unknown aggregation error" : error,
                Timestamp.from(Instant.now()),
                uploadId
        );
    }
}
