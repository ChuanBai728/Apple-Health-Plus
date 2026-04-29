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

@Service
public class AggregationPipelineService {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public AggregationPipelineService(JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void process(AggregateJobMessage message) {
        try {
            doAggregate(message);
            triggerAiSummary(message);
        } catch (Exception e) {
            markFailed(message.uploadId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    private void doAggregate(AggregateJobMessage message) {
        markAggregating(message.uploadId());
        rebuildDailyMetrics(message.uploadId());
        rebuildWeeklyMetrics(message.uploadId());
        rebuildMonthlyMetrics(message.uploadId());
        computeBaselinesAndTrends(message.uploadId());
        markReady(message.uploadId());
    }

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

    private void markAggregating(UUID uploadId) {
        jdbcTemplate.update("update uploads set status = ? where id = ?", "AGGREGATING", uploadId);
    }

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

    private void markReady(UUID uploadId) {
        jdbcTemplate.update(
                "update uploads set status = ?, finished_at = ?, last_error = null where id = ?",
                "READY",
                Timestamp.from(Instant.now()),
                uploadId
        );
    }

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
