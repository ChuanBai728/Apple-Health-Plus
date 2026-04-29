package app.healthplus.aggregation.config;

public final class QueueNames {
    public static final String AGGREGATE_QUEUE = "health.aggregate.queue";
    public static final String AGGREGATE_DLQ = "health.aggregate.dlq";
    public static final String AI_QUEUE = "health.ai.queue";
    public static final String AI_DLQ = "health.ai.dlq";

    private QueueNames() {
    }
}
