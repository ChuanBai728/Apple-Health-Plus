package app.healthplus.parser.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded ring buffer with backpressure.
 * Producer blocks when queue is full — prevents OOM on GB-scale XML.
 */
public class RecordRingBuffer {

    static final int DEFAULT_CAPACITY = 20_000;

    private final ArrayBlockingQueue<RawRecord> queue;
    private final AtomicLong producedCount = new AtomicLong(0);
    private final AtomicLong consumedCount = new AtomicLong(0);
    private volatile boolean producerDone = false;
    private volatile Throwable producerError = null;

    public RecordRingBuffer(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public RecordRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Producer: put a record into the buffer.
     * Blocks if the queue is full (backpressure).
     * Returns false if interrupted during shutdown.
     */
    public boolean offer(RawRecord record, long timeoutMs) throws InterruptedException {
        return queue.offer(record, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void put(RawRecord record) throws InterruptedException {
        queue.put(record);
        producedCount.incrementAndGet();
    }

    /**
     * Consumer: take a record from the buffer.
     * Returns null if the producer is done AND the queue is empty.
     */
    public RawRecord poll(long timeoutMs) throws InterruptedException {
        RawRecord r = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (r != null) {
            consumedCount.incrementAndGet();
        }
        return r;
    }

    /**
     * Check if the pipeline is fully drained.
     */
    public boolean isDrained() {
        return producerDone && queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public long producedCount() {
        return producedCount.get();
    }

    public long consumedCount() {
        return consumedCount.get();
    }

    // ── Producer lifecycle ──
    public void markProducerDone() {
        this.producerDone = true;
    }

    public boolean isProducerDone() {
        return producerDone;
    }

    public void setProducerError(Throwable t) {
        this.producerError = t;
    }

    public Throwable getProducerError() {
        return producerError;
    }

    /**
     * Lightweight record — just the XML attributes needed for downstream processing.
     * Avoids creating full ParsedHealthRecord objects in the producer thread.
     */
    public record RawRecord(
            String type,
            String value,
            String unit,
            String sourceName,
            String startDate,
            String endDate
    ) {}
}
