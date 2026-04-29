package app.healthplus.parser.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrator for the high-throughput Producer-Consumer XML parsing pipeline.
 * <p>
 * Architecture:
 * <pre>
 *   [StAX Producer] ──put──▶ [RingBuffer (20K)] ──poll──▶ [VirtualThread Consumers]
 *       (1 thread)            (bounded, backpressure)       (N virtual threads)
 *                                                                  │
 *                                                    ┌─────────────┘
 *                                                    ▼
 *                                          [PgCopyBatchInserter]
 *                                          (COPY FROM STDIN, 10K/batch or 500ms)
 * </pre>
 */
public class HighThroughputParsePipeline implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HighThroughputParsePipeline.class);

    private final DataSource dataSource;
    private final RecordRingBuffer buffer;
    private final PgCopyBatchInserter inserter;
    private final int consumerCount;

    private final AtomicLong recordCount = new AtomicLong(0);
    private volatile Instant startTime;
    private volatile Instant endTime;

    public HighThroughputParsePipeline(DataSource dataSource, UUID userId, UUID uploadId,
                                       int bufferCapacity, int consumerCount,
                                       int batchSize, long flushIntervalMs) {
        this.dataSource = dataSource;
        this.buffer = new RecordRingBuffer(bufferCapacity);
        this.inserter = new PgCopyBatchInserter(dataSource, userId, uploadId, batchSize, flushIntervalMs);
        this.consumerCount = consumerCount;
    }

    public HighThroughputParsePipeline(DataSource dataSource, UUID userId, UUID uploadId) {
        this(dataSource, userId, uploadId, 20_000, 4, 10_000, 500);
    }

    /**
     * Run the full pipeline: parse XML stream → process → COPY into DB.
     *
     * @param inputStream the XML input stream (from the ZIP export)
     * @return total records parsed and inserted
     */
    public long execute(InputStream inputStream) throws Exception {
        startTime = Instant.now();
        log.info("Pipeline starting: buffer={}, consumers={}, batch={}",
                buffer.remainingCapacity(), consumerCount, PgCopyBatchInserter.BATCH_SIZE);

        // Error tracking
        CountDownLatch errorLatch = new CountDownLatch(1);

        // Producer thread (platform thread for StAX)
        XmlRecordProducer producer = new XmlRecordProducer(inputStream, buffer);
        Thread producerThread = Thread.ofPlatform()
                .name("xml-producer")
                .uncaughtExceptionHandler((t, e) -> {
                    log.error("Producer crashed", e);
                    buffer.setProducerError(e);
                    buffer.markProducerDone();
                    errorLatch.countDown();
                })
                .start(producer);

        // Consumer threads (virtual threads)
        Thread[] consumers = new Thread[consumerCount];
        VirtualThreadConsumer[] consumerRefs = new VirtualThreadConsumer[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            VirtualThreadConsumer c = new VirtualThreadConsumer(buffer, inserter);
            consumerRefs[i] = c;
            consumers[i] = Thread.ofVirtual()
                    .name("xml-consumer-" + i)
                    .uncaughtExceptionHandler((t, e) -> {
                        log.error("Consumer crashed", e);
                        errorLatch.countDown();
                    })
                    .start(c);
        }

        // Wait for producer to finish
        producerThread.join();

        // Wait for buffer to drain
        while (!buffer.isDrained()) {
            Thread.sleep(50);
        }

        // Shutdown consumers
        for (VirtualThreadConsumer c : consumerRefs) {
            c.shutdown();
        }

        // Wait for all consumers with timeout
        for (Thread t : consumers) {
            t.join(TimeUnit.MINUTES.toMillis(2));
        }

        // Final flush + index rebuild
        inserter.close();

        endTime = Instant.now();
        long total = inserter.totalWritten();
        Duration elapsed = Duration.between(startTime, endTime);
        double rate = total * 1000.0 / Math.max(elapsed.toMillis(), 1);

        log.info("Pipeline complete: {} records in {} ({} rec/s)", total, elapsed, String.format("%.0f", rate));

        // Check for errors
        if (buffer.getProducerError() != null) {
            throw new RuntimeException("Pipeline failed: producer error", buffer.getProducerError());
        }

        return total;
    }

    public long getRecordCount() {
        return inserter.totalWritten();
    }

    @Override
    public void close() {
        inserter.close();
    }
}
