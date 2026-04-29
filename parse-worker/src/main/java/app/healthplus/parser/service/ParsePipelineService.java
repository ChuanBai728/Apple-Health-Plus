package app.healthplus.parser.service;

import app.healthplus.messaging.AggregateJobMessage;
import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.parser.config.QueueNames;
import app.healthplus.parser.core.AppleHealthParser;
import app.healthplus.parser.core.AppleHealthZipReader;
import app.healthplus.parser.core.HighThroughputParsePipeline;
import app.healthplus.parser.core.ParsedHealthRecord;
import app.healthplus.storage.StorageService;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ParsePipelineService {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final StorageService storageService;
    private final AppleHealthZipReader zipReader;
    private final AppleHealthParser parser;
    private final DataSource dataSource;

    public ParsePipelineService(
            JdbcTemplate jdbcTemplate,
            RabbitTemplate rabbitTemplate,
            StorageService storageService,
            AppleHealthZipReader zipReader,
            AppleHealthParser parser,
            DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.storageService = storageService;
        this.zipReader = zipReader;
        this.parser = parser;
        this.dataSource = dataSource;
    }

    public void process(ParseJobMessage message) throws Exception {
        markParsing(message.uploadId());

        try {
            AtomicLong recordCount = new AtomicLong();
            HashSet<String> sources = new HashSet<>();
            Instant[] minMaxStart = {null, null};

            try (InputStream zipStream = storageService.load(message.storageKey())) {
                AppleHealthZipReader.HealthXmlResult healthXml =
                        zipReader.openHealthXmlSinglePass(zipStream);

                if (healthXml.inputStream() != null) {
                    parseFromStream(healthXml.inputStream(), message, recordCount, sources, minMaxStart);
                } else {
                    try (InputStream zipStream2 = storageService.load(message.storageKey());
                         InputStream xmlIn = zipReader.openEntry(zipStream2, healthXml.entryName())) {
                        parseFromStream(xmlIn, message, recordCount, sources, minMaxStart);
                    }
                }
            }

            markParsed(message.uploadId(), recordCount.get(), sources.size(),
                    minMaxStart[0], minMaxStart[1]);

            rabbitTemplate.convertAndSend(
                    QueueNames.AGGREGATE_QUEUE,
                    new AggregateJobMessage(
                            message.uploadId(), message.userId(),
                            message.messageId(), "parse-worker", Instant.now()));
        } catch (Exception ex) {
            markFailed(message.uploadId(), ex.getMessage());
            throw ex;
        }
    }

    private void markParsing(UUID uploadId) {
        jdbcTemplate.update(
                "update uploads set status = ?, started_at = ?, last_error = null where id = ?",
                "PARSING", Timestamp.from(Instant.now()), uploadId);
    }

    private void markParsed(UUID uploadId, long recordCount, int sourceCount,
                            Instant minStart, Instant maxEnd) {
        jdbcTemplate.update(
                "update uploads set status = ?, record_count = ?, source_count = ?, " +
                "coverage_start_at = ?, coverage_end_at = ? where id = ?",
                "PARSED", recordCount, sourceCount,
                minStart == null ? null : Timestamp.from(minStart),
                maxEnd == null ? null : Timestamp.from(maxEnd),
                uploadId);
    }

    private void markFailed(UUID uploadId, String error) {
        jdbcTemplate.update(
                "update uploads set status = ?, last_error = ?, finished_at = ? where id = ?",
                "FAILED", error == null ? "Unknown parse error" : error,
                Timestamp.from(Instant.now()), uploadId);
    }

    private void parseFromStream(InputStream xmlInput, ParseJobMessage message,
                                  AtomicLong recordCount, HashSet<String> sources,
                                  Instant[] minMaxStart) throws Exception {
        try (CopyLoader loader = new CopyLoader(dataSource, message.userId(), message.uploadId())) {
            long count = parser.parseXmlStreaming(xmlInput, record -> {
                try {
                    loader.writeRecord(
                            record.recordType(), record.metricKey(), record.categoryKey(),
                            record.sourceName(), record.valueNumeric(), record.valueText(),
                            record.unit(), record.startAt(), record.endAt(), record.rawPayloadJson()
                    );
                    recordCount.incrementAndGet();
                    if (record.sourceName() != null && !record.sourceName().isBlank()) {
                        synchronized (sources) { sources.add(record.sourceName()); }
                    }
                    synchronized (minMaxStart) {
                        if (minMaxStart[0] == null || record.startAt().isBefore(minMaxStart[0]))
                            minMaxStart[0] = record.startAt();
                        if (minMaxStart[1] == null || record.endAt().isAfter(minMaxStart[1]))
                            minMaxStart[1] = record.endAt();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            recordCount.set(count);
        }
    }

    /**
     * High-throughput pipeline using Producer-Consumer pattern with virtual threads,
     * bounded ring buffer, and PostgreSQL COPY batch insertion.
     * <p>
     * Architecture:
     *   1 Platform Thread (StAX Producer) → RingBuffer (20K) → 4 Virtual Thread Consumers
     *   → PgCopyBatchInserter (10K records or 500ms per batch, COPY protocol)
     */
    public void processHighThroughput(ParseJobMessage message) throws Exception {
        markParsing(message.uploadId());
        try {
            UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

            long total;
            try (InputStream zipStream = storageService.load(message.storageKey())) {
                AppleHealthZipReader.HealthXmlResult result = zipReader.openHealthXmlSinglePass(zipStream);
                HighThroughputParsePipeline pipeline = new HighThroughputParsePipeline(
                        dataSource, userId, message.uploadId());
                total = pipeline.execute(result.inputStream());
            }

            // Update upload metadata
            jdbcTemplate.update(
                    "UPDATE uploads SET status = 'PARSED', record_count = ?, " +
                    "source_count = (SELECT count(DISTINCT source_name) FROM health_records WHERE upload_id = ?), " +
                    "coverage_start_at = (SELECT min(start_at) FROM health_records WHERE upload_id = ?), " +
                    "coverage_end_at = (SELECT max(end_at) FROM health_records WHERE upload_id = ?), " +
                    "finished_at = ? WHERE id = ?",
                    total, message.uploadId(), message.uploadId(), message.uploadId(),
                    Timestamp.from(Instant.now()), message.uploadId());

            // Send aggregate job
            String msgId = UUID.randomUUID().toString();
            rabbitTemplate.convertAndSend(
                    QueueNames.AGGREGATE_QUEUE,
                    new AggregateJobMessage(message.uploadId(), userId, msgId, "parse-worker", Instant.now()));

        } catch (Exception e) {
            markFailed(message.uploadId(), e.getMessage());
            throw e;
        }
    }
}
