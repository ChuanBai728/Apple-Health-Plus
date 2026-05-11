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
 * 高性能 PostgreSQL COPY 协议批量写入器。
 * <p>
 * 设计思路：
 * <ol>
 *   <li>绕过常规 INSERT 语句，直接使用 PostgreSQL 的 {@code COPY ... FROM STDIN} 协议，
 *       吞吐量可比逐条 INSERT 提升 5-10 倍。</li>
 *   <li>采用"批量缓冲 + 双阈值刷新"策略：当缓冲记录数达到 {@link #batchSize} 或
 *       距上次刷新超过 {@link #flushIntervalMs} 毫秒时触发写入，兼顾吞吐与实时性。</li>
 *   <li>写入前先删除索引（{@link #prepareDatabase()}），写入完成后重建索引（{@link #rebuildIndexes()}），
 *       避免大批量插入时索引维护带来的随机 I/O 开销。</li>
 *   <li>线程安全：内部使用 {@link ReentrantLock} 保护批量缓冲区，支持多个虚拟线程并发调用
 *       {@link #append(ParsedRecord)}。</li>
 * </ol>
 *
 * 在数据处理流水线中的位置：
 * <pre>
 *   [XML 解析] → RecordRingBuffer → VirtualThreadConsumer → [PgCopyBatchInserter] → PostgreSQL
 * </pre>
 * 本类处于流水线末端，负责将解析完成的结构化记录高效持久化到 {@code health_records} 表。
 *
 * @see VirtualThreadConsumer  上游消费者，负责解析原始 XML 并调用本类的 append 方法
 * @see ParsedRecord           待写入的结构化健康记录
 */
public class PgCopyBatchInserter implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(PgCopyBatchInserter.class);

    /** 默认批量大小：每累积 10000 条记录触发一次 COPY 写入 */
    static final int BATCH_SIZE = 10_000;
    /** 默认刷新间隔：超过 500ms 未刷新则强制写入，保证低流量时的实时性 */
    static final long FLUSH_INTERVAL_MS = 500;

    /** 批量加载前删除索引的 DDL，减少写入时的索引维护开销 */
    private static final String DROP_INDEXES = """
            DROP INDEX IF EXISTS idx_health_records_upload_metric_start;
            DROP INDEX IF EXISTS idx_health_records_user_metric_start;
            DROP INDEX IF EXISTS idx_health_records_upload_category;
            """;

    /** 批量加载完成后重建索引的 DDL */
    private static final String RECREATE_INDEXES = """
            CREATE INDEX IF NOT EXISTS idx_health_records_upload_metric_start
                ON health_records (upload_id, metric_key, start_at);
            CREATE INDEX IF NOT EXISTS idx_health_records_user_metric_start
                ON health_records (user_id, metric_key, start_at);
            CREATE INDEX IF NOT EXISTS idx_health_records_upload_category
                ON health_records (upload_id, category_key);
            """;

    /**
     * COPY 协议 SQL：使用 CSV 格式，\N 表示 NULL 值。
     * 列顺序必须与 flushBatch 中写入的 CSV 列顺序严格一致。
     */
    static final String COPY_SQL =
            "COPY health_records (user_id, upload_id, record_type, metric_key, category_key, " +
            "source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json) " +
            "FROM STDIN WITH (FORMAT csv, NULL '\\N')";

    private final DataSource dataSource;
    /** 本次上传所属用户 ID，写入每条记录时附加 */
    private final UUID userId;
    /** 本次上传任务 ID，用于关联一次 export.zip 解析的所有记录 */
    private final UUID uploadId;
    /** 触发 COPY 写入的记录数阈值 */
    private final int batchSize;
    /** 触发 COPY 写入的时间间隔（毫秒） */
    private final long flushIntervalMs;

    /** 记录缓冲区，达到 batchSize 或时间窗口后整体刷新到数据库 */
    private final List<ParsedRecord> batch = new ArrayList<>(BATCH_SIZE);
    /** 保护 batch 的并发访问：append 和 flush 可能被不同虚拟线程调用 */
    private final ReentrantLock lock = new ReentrantLock();
    /** 累计已写入数据库的总记录数，用于日志统计 */
    private final AtomicLong totalWritten = new AtomicLong(0);
    /** 上次刷新时间戳，用于时间窗口阈值判断 */
    private volatile long lastFlushTime = System.currentTimeMillis();
    /** 关闭标志，防止重复 close */
    private volatile boolean closed = false;

    /**
     * 构造函数，同时自动执行索引删除以准备批量加载。
     *
     * @param dataSource     数据库连接池
     * @param userId         当前用户 ID
     * @param uploadId       当前上传任务 ID
     * @param batchSize      触发 COPY 写入的记录数阈值
     * @param flushIntervalMs 触发 COPY 写入的时间间隔（毫秒）
     */
    public PgCopyBatchInserter(DataSource dataSource, UUID userId, UUID uploadId,
                               int batchSize, long flushIntervalMs) {
        this.dataSource = dataSource;
        this.userId = userId;
        this.uploadId = uploadId;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        // 初始化时即删除索引，为后续大批量写入做准备
        prepareDatabase();
    }

    /**
     * 使用默认批量大小（10000）和刷新间隔（500ms）的便捷构造函数。
     */
    public PgCopyBatchInserter(DataSource dataSource, UUID userId, UUID uploadId) {
        this(dataSource, userId, uploadId, BATCH_SIZE, FLUSH_INTERVAL_MS);
    }

    /**
     * 批量加载前的数据库准备：删除 health_records 表上的索引。
     * <p>
     * 删除索引的原因：大批量 COPY 写入时，每插入一行都需要更新索引的 B-Tree 结构，
     * 产生大量随机 I/O。先删除索引、写完再重建，总体速度更快。
     * 异常不中断流程，因为即使索引删除失败，COPY 写入仍然可以正常进行。
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
     * 将一条已解析的健康记录追加到批量缓冲区。
     * <p>
     * 线程安全：多个虚拟线程可并发调用。内部使用 {@link ReentrantLock} 保证互斥。
     * 当满足以下任一条件时自动触发刷新：
     * <ul>
     *   <li>缓冲区记录数 >= {@link #batchSize}</li>
     *   <li>距离上次刷新 >= {@link #flushIntervalMs} 毫秒</li>
     * </ul>
     * 刷新操作在锁外执行，避免持有锁期间执行耗时的 I/O 操作。
     *
     * @param record 已解析的健康记录
     * @throws Exception COPY 写入数据库失败时抛出
     */
    public void append(ParsedRecord record) throws Exception {
        List<ParsedRecord> toFlush = null;
        lock.lock();
        try {
            batch.add(record);
            long now = System.currentTimeMillis();
            // 双阈值判断：记录数达到上限 或 时间窗口已过期
            if (batch.size() >= batchSize || (now - lastFlushTime) >= flushIntervalMs) {
                // 将当前批次快照取出，清空缓冲区，释放锁后再执行 I/O
                toFlush = new ArrayList<>(batch);
                batch.clear();
                lastFlushTime = now;
            }
        } finally {
            lock.unlock();
        }
        // 在锁外执行数据库写入，避免阻塞其他线程的 append 操作
        if (toFlush != null && !toFlush.isEmpty()) {
            flushBatch(toFlush);
        }
    }

    /**
     * 强制刷新缓冲区中所有剩余记录。
     * <p>
     * 必须在 {@link #close()} 之前调用，确保不满一批的尾部记录不被丢失。
     * 同样采用"锁内取数据、锁外写入"的模式。
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
     * 核心写入方法：将一批记录通过 PostgreSQL COPY 协议写入数据库。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>将记录序列化为 CSV 格式的字节流（内存中完成，避免磁盘 I/O）</li>
     *   <li>通过 JDBC {@link CopyManager#copyIn} 将 CSV 字节流以流式方式发送给 PostgreSQL</li>
     *   <li>PostgreSQL 服务端直接解析 CSV 并批量写入，跳过常规的 SQL 解析和执行计划</li>
     * </ol>
     * 预分配 ByteArrayOutputStream 容量为 records.size() * 256 字节，
     * 基于每条记录约 256 字节的经验估算，减少动态扩容次数。
     *
     * @param records 待写入的记录列表
     * @throws Exception 数据库连接或 COPY 写入失败时抛出
     */
    private void flushBatch(List<ParsedRecord> records) throws Exception {
        Instant start = Instant.now();
        // 按每条约 256 字节估算初始容量，减少 ByteArrayOutputStream 的内部扩容
        ByteArrayOutputStream baos = new ByteArrayOutputStream(records.size() * 256);
        BufferedOutputStream bos = new BufferedOutputStream(baos, 8192);

        // userId 和 uploadId 每行都相同，预先序列化为字节数组避免重复转换
        byte[] uid = userId.toString().getBytes(StandardCharsets.UTF_8);
        byte[] upid = uploadId.toString().getBytes(StandardCharsets.UTF_8);

        for (ParsedRecord r : records) {
            // CSV 列顺序必须与 COPY_SQL 中声明的列顺序严格一致
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
            bos.write('\n'); // 行分隔符
        }
        bos.flush();

        // 使用 PostgreSQL JDBC 驱动的 CopyManager 执行 COPY FROM STDIN
        try (Connection conn = dataSource.getConnection()) {
            BaseConnection baseConn = conn.unwrap(BaseConnection.class);
            CopyManager cm = new CopyManager(baseConn);
            cm.copyIn(COPY_SQL, new java.io.ByteArrayInputStream(baos.toByteArray()));
        }

        totalWritten.addAndGet(records.size());
        // 计算写入速率，用于性能监控
        Duration elapsed = Duration.between(start, Instant.now());
        double rate = records.size() * 1000.0 / Math.max(elapsed.toMillis(), 1);
        log.debug("COPY flush: {} records in {}ms ({:.0f} rec/s), total={}",
                records.size(), elapsed.toMillis(), rate, totalWritten.get());
    }

    /**
     * 返回累计已写入数据库的总记录数。
     *
     * @return 已写入记录总数
     */
    public long totalWritten() {
        return totalWritten.get();
    }

    /**
     * 关闭写入器：刷新剩余缓冲记录，然后重建索引。
     * <p>
     * 实现 {@link Closeable} 接口，支持 try-with-resources 自动关闭。
     * 使用 volatile boolean 保证幂等性，防止重复重建索引。
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        flushRemaining();   // 确保尾部不满一批的记录不丢失
        rebuildIndexes();   // 批量加载完成后重建索引，恢复查询性能
    }

    /**
     * 批量加载完成后重建索引，恢复查询性能。
     * <p>
     * 使用 {@code CREATE INDEX IF NOT EXISTS} 保证幂等性——即使索引已存在也不会报错。
     * 如果重建失败（例如连接中断），记录错误日志但不阻断流程，
     * 因为缺少索引只影响查询性能，不影响数据完整性。
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

    // ── CSV 编码辅助方法 ──
    // 以下方法直接操作 OutputStream 写入字节，避免 String 拼接和中间对象分配，
    // 在大批量写入场景下显著降低 GC 压力。

    /**
     * 将字符串值按 CSV 格式写入输出流。
     * <p>
     * 处理规则：
     * <ul>
     *   <li>null 或空字符串 → 写入空（PostgreSQL 会使用列默认值或 NULL）</li>
     *   <li>不含特殊字符 → 直接写入原始字节</li>
     *   <li>包含逗号、双引号、换行符 → 用双引号包裹，内部双引号转义为两个双引号</li>
     * </ul>
     *
     * @param os    输出流
     * @param value 待写入的字符串值
     */
    private static void writeCsv(OutputStream os, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }
        // 检查是否需要双引号包裹（CSV 标准：含分隔符/引号/换行时必须包裹）
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (needsQuote) {
            os.write('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') os.write('"'); // CSV 转义：双引号 → 两个双引号
                os.write(c);
            }
            os.write('"');
        } else {
            os.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 将 Double 值按 CSV 格式写入输出流，null 值写为 \N（COPY 协议的 NULL 标记）。
     * <p>
     * 优化：当小数部分为零时（如 70.0），输出整数形式（70），减少字节数。
     *
     * @param os 输出流
     * @param v  待写入的数值，可为 null
     */
    private static void writeDoubleOrNull(OutputStream os, Double v) throws IOException {
        if (v == null) {
            // \N 是 COPY 协议中 NULL 的默认标记
            os.write('\\'); os.write('N');
        } else if (v == v.longValue()) {
            // 整数值（如 70.0）输出为 "70" 而非 "70.0"，节省空间
            os.write(Long.toString(v.longValue()).getBytes(StandardCharsets.UTF_8));
        } else {
            os.write(Double.toString(v).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 将时间戳按 ISO-8601 格式写入输出流，epoch 或 null 写为 \N。
     *
     * @param os 输出流
     * @param ts 待写入的时间戳，可为 null
     */
    private static void writeTimestamp(OutputStream os, java.time.Instant ts) throws IOException {
        if (ts == null || ts.equals(java.time.Instant.EPOCH)) {
            // EPOCH 视为无效时间戳，写入 NULL
            os.write('\\'); os.write('N');
        } else {
            // Instant.toString() 输出 ISO-8601 格式，如 "2024-01-15T08:30:00Z"
            os.write(ts.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 已完全解析的健康记录，可直接用于数据库写入。
     * <p>
     * 使用 Java record 类型，自动生成 getter、equals、hashCode 和 toString。
     * 字段顺序与 COPY_SQL 中的列声明一一对应。
     *
     * @param recordType      记录类型，如 "record" 或 "workout"
     * @param metricKey       指标键名，如 "heart_rate"、"step_count"
     * @param categoryKey     分类键名，如 "heart_cardio"、"daily_activity"
     * @param sourceName      数据来源设备名，如 "Apple Watch"
     * @param valueNumeric    数值型测量值（可为 null，如睡眠分析等分类数据）
     * @param valueText       原始文本值
     * @param unit            单位，如 "bpm"、"kg"、"%"
     * @param startAt         记录开始时间
     * @param endAt           记录结束时间
     * @param rawPayloadJson  原始载荷的 JSON 表示，用于审计和调试
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
