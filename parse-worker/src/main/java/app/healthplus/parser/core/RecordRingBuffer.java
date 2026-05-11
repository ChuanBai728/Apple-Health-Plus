package app.healthplus.parser.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界环形缓冲区（Ring Buffer），实现生产者-消费者模式的背压控制。
 * <p>
 * 职责：作为 StAX XML 生产者和虚拟线程消费者之间的中间层，
 * 通过固定容量的阻塞队列实现流量控制。当队列满时生产者自动阻塞，
 * 防止 GB 级 XML 文件解析时导致内存溢出（OOM）。
 * </p>
 * <p>
 * 设计思路：
 * <ul>
 *   <li>基于 {@link ArrayBlockingQueue} 实现，天然支持线程安全和阻塞语义</li>
 *   <li>容量默认 20K 条记录，约占用 10-20MB 内存，平衡内存使用和吞吐量</li>
 *   <li>使用 {@link AtomicLong} 追踪生产和消费计数，支持监控和调试</li>
 *   <li>使用 volatile 标志位跟踪生产者生命周期，支持优雅关闭</li>
 *   <li>内部定义轻量级 {@link RawRecord}，避免在生产者线程创建完整对象</li>
 * </ul>
 * </p>
 * <p>
 * 线程安全性：所有公开方法均为线程安全，可被多个线程并发调用。
 * 生产者通过 {@link #put(RawRecord)} 写入，消费者通过 {@link #poll(long)} 读取。
 * </p>
 */
public class RecordRingBuffer {

    /** 默认缓冲区容量：20K 条记录，约 10-20MB 内存 */
    static final int DEFAULT_CAPACITY = 20_000;

    /**
     * 底层阻塞队列，提供线程安全的 FIFO 语义。
     * ArrayBlockingQueue 内部使用 ReentrantLock + Condition 实现阻塞，
     * 比 LinkedBlockingQueue 更节省内存（无节点对象开销）。
     */
    private final ArrayBlockingQueue<RawRecord> queue;

    /** 生产者已放入的记录总数（原子计数，支持监控） */
    private final AtomicLong producedCount = new AtomicLong(0);

    /** 消费者已取出的记录总数（原子计数，支持监控） */
    private final AtomicLong consumedCount = new AtomicLong(0);

    /**
     * 生产者完成标志。
     * volatile 保证多线程可见性：消费者线程能立即看到生产者状态变化。
     * 生产者完成解析后设置此标志，消费者据此判断是否继续等待。
     */
    private volatile boolean producerDone = false;

    /**
     * 生产者异常信息。
     * 当生产者线程发生未捕获异常时，通过此字段传递错误给管道编排器，
     * 以便向上层报告失败原因。
     */
    private volatile Throwable producerError = null;

    /**
     * 使用指定容量构造环形缓冲区。
     *
     * @param capacity 缓冲区最大容量，必须大于 0
     */
    public RecordRingBuffer(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 使用默认容量（20K）构造环形缓冲区。
     */
    public RecordRingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 生产者接口：将记录放入缓冲区（带超时的非阻塞版本）。
     * <p>
     * 如果缓冲区已满，等待指定时间后返回 false，而非无限阻塞。
     * 适用于需要快速失败或实现自定义重试逻辑的场景。
     * </p>
     *
     * @param record    要放入的原始记录
     * @param timeoutMs 等待超时时间（毫秒）
     * @return true 如果成功放入，false 如果超时或被中断
     * @throws InterruptedException 如果等待期间线程被中断
     */
    public boolean offer(RawRecord record, long timeoutMs) throws InterruptedException {
        return queue.offer(record, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 生产者接口：将记录放入缓冲区（阻塞版本）。
     * <p>
     * 如果缓冲区已满，生产者线程会阻塞等待，直到有空间可用。
     * 这是实现背压的核心机制：当消费者处理速度跟不上时，
     * 生产者自动减速，防止内存无限增长。
     * </p>
     * <p>
     * 注意：此方法会同时递增生产计数器，用于监控和进度报告。
     * </p>
     *
     * @param record 要放入的原始记录
     * @throws InterruptedException 如果等待期间线程被中断
     */
    public void put(RawRecord record) throws InterruptedException {
        queue.put(record);
        producedCount.incrementAndGet();
    }

    /**
     * 消费者接口：从缓冲区取出一条记录（带超时的阻塞版本）。
     * <p>
     * 如果缓冲区为空，等待指定时间后返回 null。
     * 消费者据此判断是否应该退出循环。
     * </p>
     * <p>
     * 返回 null 的两种情况：
     * <ol>
     *   <li>超时：缓冲区暂时为空，但生产者可能还在工作</li>
     *   <li>生产者已完成且缓冲区已排空：消费者应退出</li>
     * </ol>
     * 消费者需要结合 {@link #isProducerDone()} 判断是哪种情况。
     * </p>
     *
     * @param timeoutMs 等待超时时间（毫秒）
     * @return 取出的记录，如果超时或缓冲区已排空则返回 null
     * @throws InterruptedException 如果等待期间线程被中断
     */
    public RawRecord poll(long timeoutMs) throws InterruptedException {
        RawRecord r = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (r != null) {
            consumedCount.incrementAndGet();
        }
        return r;
    }

    /**
     * 检查管道是否已完全排空。
     * <p>
     * 两个条件必须同时满足：
     * <ol>
     *   <li>生产者已标记完成（不再有新记录）</li>
     *   <li>缓冲区为空（所有记录已被消费）</li>
     * </ol>
     * 管道编排器（HighThroughputParsePipeline）据此判断是否可以关闭消费者。
     * </p>
     *
     * @return true 如果生产者完成且缓冲区为空
     */
    public boolean isDrained() {
        return producerDone && queue.isEmpty();
    }

    /**
     * 获取缓冲区当前大小（近似值）。
     * <p>
     * 注意：在并发环境下，此值可能在返回时已经变化，仅用于监控和调试。
     * </p>
     *
     * @return 缓冲区中的记录数量
     */
    public int size() {
        return queue.size();
    }

    /**
     * 获取缓冲区剩余容量（近似值）。
     * <p>
     * 用于启动时日志报告，展示缓冲区配置。
     * </p>
     *
     * @return 缓冲区还可容纳的记录数量
     */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * 获取生产者已放入的记录总数。
     * <p>
     * 用于监控和调试，可与 {@link #consumedCount()} 对比
     * 计算缓冲区中的滞留记录数。
     * </p>
     *
     * @return 生产者已放入的记录总数
     */
    public long producedCount() {
        return producedCount.get();
    }

    /**
     * 获取消费者已取出的记录总数。
     * <p>
     * 用于监控和调试，可与 {@link #producedCount()} 对比
     * 计算缓冲区中的滞留记录数。
     * </p>
     *
     * @return 消费者已取出的记录总数
     */
    public long consumedCount() {
        return consumedCount.get();
    }

    // ── 生产者生命周期管理 ──

    /**
     * 标记生产者已完成工作。
     * <p>
     * 生产者线程在完成 XML 解析后调用此方法。
     * 设置后，消费者可以通过 {@link #isDrained()} 判断是否可以退出。
     * </p>
     */
    public void markProducerDone() {
        this.producerDone = true;
    }

    /**
     * 检查生产者是否已完成工作。
     * <p>
     * 消费者在 poll() 返回 null 时调用此方法，
     * 判断是应该退出还是继续等待。
     * </p>
     *
     * @return true 如果生产者已标记完成
     */
    public boolean isProducerDone() {
        return producerDone;
    }

    /**
     * 记录生产者发生的异常。
     * <p>
     * 生产者的 UncaughtExceptionHandler 调用此方法，
     * 将错误信息传递给管道编排器，以便向上层报告。
     * </p>
     *
     * @param t 生产者线程抛出的异常
     */
    public void setProducerError(Throwable t) {
        this.producerError = t;
    }

    /**
     * 获取生产者发生的异常。
     * <p>
     * 管道编排器在执行完成后调用此方法检查是否有错误发生。
     * 如果返回非 null，说明生产者线程异常，管道执行失败。
     * </p>
     *
     * @return 生产者异常，如果无异常则返回 null
     */
    public Throwable getProducerError() {
        return producerError;
    }

    /**
     * 轻量级原始记录，仅包含下游处理所需的 XML 属性。
     * <p>
     * 设计目的：
     * <ul>
     *   <li>减少生产者线程的内存分配：避免创建完整的 ParsedHealthRecord 对象</li>
     *   <li>降低序列化开销：字符串属性直接从 XML 属性提取，无需类型转换</li>
     *   <li>职责分离：生产者只负责提取原始数据，类型转换和验证交给消费者</li>
     * </ul>
     * </p>
     * <p>
     * 使用 Java 16+ 的 record 语法：自动生成构造器、equals()、hashCode()、toString()。
     * 不可变对象，天然线程安全，适合在生产者-消费者之间传递。
     * </p>
     */
    public record RawRecord(
            /** 健康记录类型（如 "HKQuantityTypeIdentifierStepCount"） */
            String type,
            /** 记录值（字符串形式，下游负责类型转换） */
            String value,
            /** 计量单位（如 "count", "mg/dL", "kcal"） */
            String unit,
            /** 数据来源（如 "Apple Watch", "iPhone"） */
            String sourceName,
            /** 记录开始时间（ISO 8601 格式） */
            String startDate,
            /** 记录结束时间（ISO 8601 格式） */
            String endDate
    ) {}
}
