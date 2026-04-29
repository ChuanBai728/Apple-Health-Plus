package app.healthplus.parser.config;

public final class QueueNames {
    public static final String PARSE_QUEUE = "health.parse.queue";
    public static final String PARSE_DLQ = "health.parse.dlq";
    public static final String AGGREGATE_QUEUE = "health.aggregate.queue";

    private QueueNames() {
    }
}
