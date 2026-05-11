package app.healthplus.parser.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL COPY 协议批量写入器（标准模式）。
 *
 * <p>与 {@link app.healthplus.parser.core.PgCopyBatchInserter} 的区别：
 * CopyLoader 使用临时文件中转 CSV，close() 时一次性 COPY 导入；
 * PgCopyBatchInserter 使用内存 ByteArrayOutputStream + 双阈值自动 flush。
 *
 * <p>执行流程：
 * <ol>
 *   <li>writeRecord() — 逐条写入 CSV 格式到临时文件（内存缓冲 8MB）</li>
 *   <li>close() — 删除索引 → COPY FROM STDIN → 异步重建索引 → 删除临时文件</li>
 * </ol>
 *
 * <p>先删索引再重建的原因：B-Tree 索引维护是批量写入的主要瓶颈，
 * 删除后顺序追加可达 10 万+ 行/秒，最后一次性 CREATE INDEX 远快于逐行维护。
 */
public class CopyLoader implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CopyLoader.class);

    /** 内部 BufferedOutputStream 的缓冲区大小（8MB），减少磁盘 I/O 次数 */
    private static final int BUF_SIZE = 8 * 1024 * 1024;

    private final DataSource dataSource;
    /** 临时 CSV 文件路径，close() 后自动删除 */
    private final Path tmpFile;
    private final BufferedOutputStream out;
    /** 预编码的 userId/uploadId 字节数组，避免每条记录重复编码 */
    private final byte[] uidBytes, upidBytes;
    /** 异步重建索引的线程池（单线程） */
    private final ExecutorService indexExecutor;
    /** 当前行的字节缓冲区，支持自动扩容 */
    private byte[] lineBuf;
    /** 当前行写入位置指针 */
    private int linePos;
    /** 累计写入记录数 */
    private long rowCount;
    /** 记录的时间范围边界，用于更新 upload 表的 start_at/end_at */
    private Instant minStart, maxEnd;

    /** CSV 空值标记（\N），PostgreSQL COPY 的 NULL 表示方式 */
    private static final byte[] BS_NULL = "\\N".getBytes();
    private static final byte COMMA = ',', NEWLINE = '\n', QUOTE = '"';

    /**
     * 构造函数，创建临时文件和输出流。
     *
     * @param dataSource 数据库连接源
     * @param userId 用户 ID，每条记录都会携带
     * @param uploadId 上传任务 ID，每条记录都会携带
     */
    public CopyLoader(DataSource dataSource, UUID userId, UUID uploadId) throws IOException {
        this.dataSource = dataSource;
        this.tmpFile = Files.createTempFile("health-copy-", ".csv");
        this.out = new BufferedOutputStream(Files.newOutputStream(tmpFile), BUF_SIZE);
        // 预编码 UUID 为 UTF-8 字节数组，避免每条记录重复调用 toString().getBytes()
        this.uidBytes  = userId.toString().getBytes(StandardCharsets.UTF_8);
        this.upidBytes = uploadId.toString().getBytes(StandardCharsets.UTF_8);
        this.lineBuf = new byte[1024];
        // 守护线程，JVM 退出时自动终止
        this.indexExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "copyloader-index-rebuild");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 写入一条健康记录到 CSV 缓冲区。
     *
     * <p>CSV 列顺序与 COPY 命令的列顺序严格一致：
     * user_id, upload_id, record_type, metric_key, category_key,
     * source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json
     *
     * <p>性能优化：userId/uploadId 直接写入预编码字节数组，避免重复编码。
     * valueNumeric 为 null 时写入 \N（CSV 空值标记）。
     */
    public void writeRecord(String recordType, String metricKey, String categoryKey,
            String sourceName, Double valueNumeric, String valueText,
            String unit, Instant startAt, Instant endAt, String rawPayloadJson) throws IOException {
        linePos = 0;
        writeRaw(uidBytes); writeSep();        // userId — 预编码，直接写入
        writeRaw(upidBytes); writeSep();       // uploadId — 预编码，直接写入
        writeCsv(recordType);
        writeCsv(metricKey);
        writeCsv(categoryKey);
        writeCsv(sourceName);
        // 数值型字段：null 用 \N 标记，非 null 直接转字节
        if (valueNumeric != null) { writeRaw(doubleToBytes(valueNumeric)); }
        else { writeRaw(BS_NULL); }
        writeSep();
        writeCsv(valueText);
        writeCsv(unit);
        // 时间字段直接用 Instant.toString()（ISO 8601 格式），PostgreSQL 可直接解析
        writeRaw(startAt.toString().getBytes(StandardCharsets.UTF_8)); writeSep();
        writeRaw(endAt.toString().getBytes(StandardCharsets.UTF_8));   writeSep();
        writeCsvLast(rawPayloadJson);  // 最后一列用换行符结尾而非逗号
        out.write(lineBuf, 0, linePos);
        rowCount++;
        // 维护时间范围边界
        if (minStart == null || startAt.isBefore(minStart)) minStart = startAt;
        if (maxEnd == null || endAt.isAfter(maxEnd)) maxEnd = endAt;
    }

    /** 写入 CSV 列分隔符（逗号） */
    private void writeSep() { lineBuf[linePos++] = COMMA; }

    /** 将字节数组写入行缓冲区，容量不足时自动扩容 */
    private void writeRaw(byte[] b) {
        int len = b.length;
        if (linePos + len > lineBuf.length) grow(linePos + len);
        System.arraycopy(b, 0, lineBuf, linePos, len);
        linePos += len;
    }

    /**
     * 写入一个 CSV 字段（非最后一个）。
     *
     * <p>RFC 4180 转义规则：
     * - null/空值 → \N
     * - 包含逗号/双引号/换行 → 用双引号包裹，内部双引号转义为两个双引号
     * - 普通字符串 → 直接写入
     */
    private void writeCsv(String value) {
        if (value == null || value.isEmpty()) { writeRaw(BS_NULL); writeSep(); return; }
        boolean needsQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') { needsQuote = true; break; }
        }
        if (needsQuote) {
            StringBuilder sb = new StringBuilder(value.length()+4);
            sb.append('"').append(value.replace("\"","\"\"")).append('"').append(',');
            writeRaw(sb.toString().getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] b = value.getBytes(StandardCharsets.UTF_8);
            ensure(linePos + b.length + 1);
            System.arraycopy(b, 0, lineBuf, linePos, b.length);
            linePos += b.length;
            writeSep();
        }
    }

    /**
     * 写入 CSV 最后一个字段（以换行符结尾）。
     *
     * <p>与 writeCsv 的区别：结尾用 \n 而非逗号，表示一行结束。
     */
    private void writeCsvLast(String value) {
        if (value == null || value.isEmpty()) { writeRaw(BS_NULL); lineBuf[linePos++]=NEWLINE; return; }
        boolean needsQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') { needsQuote = true; break; }
        }
        if (needsQuote) {
            writeRaw(new byte[]{QUOTE});
            byte[] b = value.replace("\"","\"\"").getBytes(StandardCharsets.UTF_8);
            ensure(linePos + b.length + 2);
            System.arraycopy(b,0,lineBuf,linePos,b.length); linePos+=b.length;
            writeRaw(new byte[]{QUOTE,NEWLINE});
        } else {
            byte[] b = value.getBytes(StandardCharsets.UTF_8);
            ensure(linePos + b.length + 1);
            System.arraycopy(b,0,lineBuf,linePos,b.length); linePos+=b.length;
            lineBuf[linePos++]=NEWLINE;
        }
    }

    /** double 转字节数组，整数值（如 72.0）输出为 "72" 而非 "72.0"，节省空间 */
    private static byte[] doubleToBytes(double d) {
        if (d == (long) d) return Long.toString((long) d).getBytes(StandardCharsets.UTF_8);
        return Double.toString(d).getBytes(StandardCharsets.UTF_8);
    }

    /** 确保行缓冲区有足够容量，不足时翻倍扩容 */
    private void ensure(int needed) { if (linePos + needed > lineBuf.length) grow(linePos + needed); }
    private void grow(int min) {
        int n = lineBuf.length * 2; while (n < min) n *= 2;
        byte[] bigger = new byte[n];
        System.arraycopy(lineBuf, 0, bigger, 0, linePos);
        lineBuf = bigger;
    }

    /**
     * 关闭写入器，执行 COPY 批量导入。
     *
     * <p>执行流程：
     * <ol>
     *   <li>关闭输出流（flush 残余数据到临时文件）</li>
     *   <li>删除 3 个索引（消除批量写入的索引维护开销）</li>
     *   <li>COPY FROM STDIN 从临时文件导入（CSV 格式，\N 为 NULL）</li>
     *   <li>异步提交索引重建任务（3 个索引并行重建）</li>
     *   <li>删除临时文件</li>
     * </ol>
     */
    @Override
    public void close() throws IOException {
        out.close();
        long start = System.currentTimeMillis();
        var fileReader = new java.io.FileReader(tmpFile.toFile(), StandardCharsets.UTF_8);
        try (Connection conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();
            // 删除索引：批量顺序写入时不维护索引，最后一次性重建
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_upload_metric_start");
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_user_metric_start");
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_upload_category");

            // 使用 PostgreSQL JDBC 驱动的 CopyManager 执行 COPY FROM STDIN
            BaseConnection baseConn = conn.unwrap(BaseConnection.class);
            CopyManager cm = new CopyManager(baseConn);
            long loaded = cm.copyIn(
                    "COPY health_records (user_id, upload_id, record_type, metric_key, category_key, " +
                    "source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json) " +
                    "FROM STDIN WITH (FORMAT csv, NULL '\\N')", fileReader);
            long copyMs = System.currentTimeMillis() - start;
            log.info("COPY loaded {} records in {}ms ({} rows/s)", loaded, copyMs,
                     copyMs > 0 ? loaded * 1000 / copyMs : 0);

            // 异步重建索引：不阻塞主流程，索引重建完成后记录日志
            indexExecutor.submit(() -> {
                try (Connection c = dataSource.getConnection()) {
                    long t = System.currentTimeMillis();
                    var s = c.createStatement();
                    s.execute("CREATE INDEX idx_health_records_upload_metric_start ON health_records (upload_id, metric_key, start_at)");
                    s.execute("CREATE INDEX idx_health_records_user_metric_start ON health_records (user_id, metric_key, start_at)");
                    s.execute("CREATE INDEX idx_health_records_upload_category ON health_records (upload_id, category_key)");
                    log.info("Background index rebuild done in {}ms", System.currentTimeMillis() - t);
                } catch (Exception e) { log.error("Index rebuild failed", e); }
            });
            indexExecutor.shutdown();
        } catch (Exception e) {
            throw new IOException("COPY failed", e);
        } finally {
            fileReader.close();
            // 清理临时文件
            try { Files.deleteIfExists(tmpFile); } catch (IOException e) { log.warn("Failed to delete temp file: {}", tmpFile, e); }
        }
    }

    public long getRowCount() { return rowCount; }
    public Instant getMinStart() { return minStart; }
    public Instant getMaxEnd() { return maxEnd; }
}
