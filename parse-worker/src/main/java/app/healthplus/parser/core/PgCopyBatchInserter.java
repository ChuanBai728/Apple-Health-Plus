package app.healthplus.parser.core;

import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance PostgreSQL COPY batch inserter.
 * <p>
 * Uses {@code CopyManager.copyIn("COPY health_records FROM STDIN ...")}
 * for maximum throughput. Dynamically batches records — flushes when
 * batch size >= threshold OR time window exceeded.
 * <p>
 * Thread-safe: multiple virtual threads can call {@link #append(ParsedRecord)}.
 */
public class PgCopyBatchInserter implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(PgCopyBatchInserter.class);

    static final int BATCH_SIZE = 10_000;
    static final long FLUSH_INTERVAL_MS = 500;

    private static final String DROP_INDEXES = """
            DROP INDEX IF EXISTS idx_health_records_upload_metric_start;
            DROP INDEX IF EXISTS idx_health_records_user_metric_start;
            DROP INDEX IF EXISTS idx_health_records_upload_category;
            """;

    private static final String RECREATE_INDEXES = """
            CREATE INDEX IF NOT EXISTS idx_health_records_upload_metric_start
                ON health_records (upload_id, metric_key, start_at);
            CREATE INDEX IF NOT EXISTS idx_health_records_user_metric_start
                ON health_records (user_id, metric_key, start_at);
            CREATE INDEX IF NOT EXISTS idx_health_records_upload_category
                ON health_records (upload_id, category_key);
            """;

    static final String COPY_SQL =
            "COPY health_records (user_id, upload_id, record_type, metric_key, category_key, " +
            "source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json) " +
            "FROM STDIN WITH (FORMAT csv, NULL '\\N')";

    private final DataSource dataSource;
    private final UUID userId;
    private final UUID uploadId;
    private final int batchSize;
    private final long flushIntervalMs;

    private final List<ParsedRecord> batch = new ArrayList<>(BATCH_SIZE);
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicLong totalWritten = new AtomicLong(0);
    private volatile long lastFlushTime = System.currentTimeMillis();
    private volatile boolean closed = false;

    public PgCopyBatchInserter(DataSource dataSource, UUID userId, UUID uploadId,
                               int batchSize, long flushIntervalMs) {
        this.dataSource = dataSource;
        this.userId = userId;
        this.uploadId = uploadId;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        prepareDatabase();
    }

    public PgCopyBatchInserter(DataSource dataSource, UUID userId, UUID uploadId) {
        this(dataSource, userId, uploadId, BATCH_SIZE, FLUSH_INTERVAL_MS);
    }

    /**
     * Drop indexes before bulk load for maximum ingest speed.
     */
    private void prepareDatabase() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DROP_INDEXES);
            log.info("Indexes dropped for bulk load");
        } catch (Exception e) {
            log.warn("Failed to drop indexes (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Append a parsed record to the batch.
     * Automatically flushes if the batch is full or the time window has elapsed.
     */
    public void append(ParsedRecord record) throws Exception {
        List<ParsedRecord> toFlush = null;
        lock.lock();
        try {
            batch.add(record);
            long now = System.currentTimeMillis();
            if (batch.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
                toFlush = new ArrayList<>(batch);
                batch.clear();
                lastFlushTime = now;
            }
        } finally {
            lock.unlock();
        }
        if (toFlush != null && !toFlush.isEmpty()) {
            flushBatch(toFlush);
        }
    }

    /**
     * Force flush all remaining records. Must be called before close().
     */
    public void flushRemaining() {
        List<ParsedRecord> remaining;
        lock.lock();
        try {
            if (batch.isEmpty()) return;
            remaining = new ArrayList<>(batch);
            batch.clear();
        } finally {
            lock.unlock();
        }
        try {
            flushBatch(remaining);
        } catch (Exception e) {
            log.error("Failed to flush remaining {} records", remaining.size(), e);
        }
    }

    /**
     * Core flush: write a batch via PostgreSQL COPY protocol.
     */
    private void flushBatch(List<ParsedRecord> records) throws Exception {
        Instant start = Instant.now();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(records.size() * 256);
        BufferedOutputStream bos = new BufferedOutputStream(baos, 8192);

        byte[] uid = userId.toString().getBytes(StandardCharsets.UTF_8);
        byte[] upid = uploadId.toString().getBytes(StandardCharsets.UTF_8);

        for (ParsedRecord r : records) {
            // Write CSV row: user_id, upload_id, record_type, metric_key, category_key,
            //              source_name, value_numeric, value_text, unit, start_at, end_at, json
            bos.write(uid); bos.write(',');
            bos.write(upid); bos.write(',');
            writeCsv(bos, r.recordType()); bos.write(',');
            writeCsv(bos, r.metricKey()); bos.write(',');
            writeCsv(bos, r.categoryKey()); bos.write(',');
            writeCsv(bos, r.sourceName()); bos.write(',');
            writeDoubleOrNull(bos, r.valueNumeric()); bos.write(',');
            writeCsv(bos, r.valueText()); bos.write(',');
            writeCsv(bos, r.unit()); bos.write(',');
            writeTimestamp(bos, r.startAt()); bos.write(',');
            writeTimestamp(bos, r.endAt()); bos.write(',');
            writeCsv(bos, r.rawPayloadJson());
            bos.write('\n');
        }
        bos.flush();

        try (Connection conn = dataSource.getConnection()) {
            BaseConnection baseConn = conn.unwrap(BaseConnection.class);
            CopyManager cm = new CopyManager(baseConn);
            cm.copyIn(COPY_SQL, new java.io.ByteArrayInputStream(baos.toByteArray()));
        }

        totalWritten.addAndGet(records.size());
        Duration elapsed = Duration.between(start, Instant.now());
        double rate = records.size() * 1000.0 / Math.max(elapsed.toMillis(), 1);
        log.debug("COPY flush: {} records in {}ms ({:.0f} rec/s), total={}",
                records.size(), elapsed.toMillis(), rate, totalWritten.get());
    }

    public long totalWritten() {
        return totalWritten.get();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        flushRemaining();
        rebuildIndexes();
    }

    /**
     * Recreate indexes after bulk load completes.
     */
    private void rebuildIndexes() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(RECREATE_INDEXES);
            log.info("Indexes rebuilt after bulk load ({} total records)", totalWritten.get());
        } catch (Exception e) {
            log.error("Failed to rebuild indexes", e);
        }
    }

    // ── CSV encoding helpers ──

    private static void writeCsv(OutputStream os, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (needsQuote) {
            os.write('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') os.write('"');
                os.write(c);
            }
            os.write('"');
        } else {
            os.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeDoubleOrNull(OutputStream os, Double v) throws IOException {
        if (v == null) {
            os.write('\\'); os.write('N');
        } else if (v == v.longValue()) {
            os.write(Long.toString(v.longValue()).getBytes(StandardCharsets.UTF_8));
        } else {
            os.write(Double.toString(v).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeTimestamp(OutputStream os, java.time.Instant ts) throws IOException {
        if (ts == null || ts.equals(java.time.Instant.EPOCH)) {
            os.write('\\'); os.write('N');
        } else {
            os.write(ts.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Fully processed record — heavyweight, ready for DB insert.
     */
    public record ParsedRecord(
            String recordType,
            String metricKey,
            String categoryKey,
            String sourceName,
            Double valueNumeric,
            String valueText,
            String unit,
            java.time.Instant startAt,
            java.time.Instant endAt,
            String rawPayloadJson
    ) {}
}
