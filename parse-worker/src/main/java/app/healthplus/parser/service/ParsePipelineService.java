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

/**
 * 解析流水线核心服务，协调从 ZIP 读取到数据入库的完整链路。
 * <p>
 * 提供两种解析模式：
 * <ol>
 *   <li><b>标准模式 ({@link #process})</b>：StAX 流式解析 + CopyLoader 逐条 COPY 写入，
 *       适合中小数据量，保证数据源统计和时间覆盖率的精确计算</li>
 *   <li><b>高吞吐模式 ({@link #processHighThroughput})</b>：StAX Producer + 虚拟线程 Consumer
 *       + 环形缓冲区 + 批量 COPY 协议，适合百万级记录的快速导入</li>
 * </ol>
 * <p>
 * 事务边界设计：解析过程不做事务包裹（单条 COPY 不需要），状态标记通过独立的 UPDATE 完成。
 * 如果解析中途失败，会标记 FAILED 状态并向上抛出异常，由 RabbitMQ 重试机制或 DLQ 兜底。
 */
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

    /**
     * 标准解析流程：StAX 流式解析 + CopyLoader 写入。
     * <p>
     * 流程：标记 PARSING → 解压 ZIP 并流式解析 XML → COPY 写入数据库 → 标记 PARSED → 发送聚合任务。
     * 中途任何异常会标记 FAILED 并重新抛出。
     *
     * @param message 解析任务消息，包含 uploadId、userId、storageKey
     * @throws Exception 解析或数据库写入失败
     */
    public void process(ParseJobMessage message) throws Exception {
        // 先将 upload 状态标记为"解析中"，清空上次的错误信息
        markParsing(message.uploadId());

        try {
            AtomicLong recordCount = new AtomicLong();
            HashSet<String> sources = new HashSet<>();
            Instant[] minMaxStart = {null, null}; // [0]=最早时间, [1]=最晚时间

            try (InputStream zipStream = storageService.load(message.storageKey())) {
                // 单次遍历 ZIP 条目，尝试直接获取 health.xml 的 InputStream
                AppleHealthZipReader.HealthXmlResult healthXml =
                        zipReader.openHealthXmlSinglePass(zipStream);

                if (healthXml.inputStream() != null) {
                    // 命中：单次遍历已拿到 InputStream，直接解析（最优路径）
                    parseFromStream(healthXml.inputStream(), message, recordCount, sources, minMaxStart);
                } else {
                    // 未命中：需要重新打开 ZIP 按 entryName 定位（兼容不同 ZIP 结构）
                    try (InputStream zipStream2 = storageService.load(message.storageKey());
                         InputStream xmlIn = zipReader.openEntry(zipStream2, healthXml.entryName())) {
                        parseFromStream(xmlIn, message, recordCount, sources, minMaxStart);
                    }
                }
            }

            // 解析成功，写入统计元数据
            markParsed(message.uploadId(), recordCount.get(), sources.size(),
                    minMaxStart[0], minMaxStart[1]);

            // 发送聚合任务到下游 aggregation-worker
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

    /**
     * 将 upload 状态标记为"解析中"，并清除之前的错误信息。
     *
     * @param uploadId 上传记录 ID
     */
    private void markParsing(UUID uploadId) {
        jdbcTemplate.update(
                "update uploads set status = ?, started_at = ?, last_error = null where id = ?",
                "PARSING", Timestamp.from(Instant.now()), uploadId);
    }

    /**
     * 解析完成后更新统计元数据：记录数、数据源数量、时间覆盖范围。
     *
     * @param uploadId   上传记录 ID
     * @param recordCount 解析出的健康记录总数
     * @param sourceCount 不同的数据源数量（如 iPhone、Apple Watch）
     * @param minStart    所有记录中最早的时间
     * @param maxEnd      所有记录中最晚的时间
     */
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

    /**
     * 将 upload 状态标记为"失败"，记录错误信息和完成时间。
     *
     * @param uploadId 上传记录 ID
     * @param error    错误信息，null 时使用默认提示
     */
    private void markFailed(UUID uploadId, String error) {
        jdbcTemplate.update(
                "update uploads set status = ?, last_error = ?, finished_at = ? where id = ?",
                "FAILED", error == null ? "Unknown parse error" : error,
                Timestamp.from(Instant.now()), uploadId);
    }

    /**
     * 从 XML 输入流中解析健康记录并通过 CopyLoader 批量写入数据库。
     * <p>
     * 使用 StAX 流式解析避免将整个 XML 加载到内存；每解析一条记录就通过
     * CopyLoader（PostgreSQL COPY 协议）写入数据库。同时在回调中收集统计数据
     * （记录数、数据源集合、时间范围）。
     * <p>
     * 注意：sources 和 minMaxStart 在多线程场景下通过 synchronized 保护，
     * 但标准模式下回调通常在单线程中执行，这里的同步是防御性编程。
     *
     * @param xmlInput    XML 输入流
     * @param message     解析任务消息
     * @param recordCount 原子计数器，累计解析的记录数
     * @param sources     数据源名称集合（如 "iPhone", "Apple Watch"）
     * @param minMaxStart 长度为 2 的数组：[0]=最早时间, [1]=最晚时间
     * @throws Exception 解析或写入异常
     */
    private void parseFromStream(InputStream xmlInput, ParseJobMessage message,
                                  AtomicLong recordCount, HashSet<String> sources,
                                  Instant[] minMaxStart) throws Exception {
        // CopyLoader 实现 AutoCloseable，关闭时会 flush 剩余数据并释放 COPY 连接
        try (CopyLoader loader = new CopyLoader(dataSource, message.userId(), message.uploadId())) {
            long count = parser.parseXmlStreaming(xmlInput, record -> {
                try {
                    // 通过 COPY 协议逐条写入（CopyLoader 内部可能有批量缓冲）
                    loader.writeRecord(
                            record.recordType(), record.metricKey(), record.categoryKey(),
                            record.sourceName(), record.valueNumeric(), record.valueText(),
                            record.unit(), record.startAt(), record.endAt(), record.rawPayloadJson()
                    );
                    recordCount.incrementAndGet();
                    // 收集不重复的数据源名称
                    if (record.sourceName() != null && !record.sourceName().isBlank()) {
                        synchronized (sources) { sources.add(record.sourceName()); }
                    }
                    // 维护全局最早/最晚时间边界
                    synchronized (minMaxStart) {
                        if (minMaxStart[0] == null || record.startAt().isBefore(minMaxStart[0]))
                            minMaxStart[0] = record.startAt();
                        if (minMaxStart[1] == null || record.endAt().isAfter(minMaxStart[1]))
                            minMaxStart[1] = record.endAt();
                    }
                } catch (Exception e) {
                    // 包装为 RuntimeException 以绕过函数式接口的 checked exception 限制
                    throw new RuntimeException(e);
                }
            });
            recordCount.set(count);
        }
    }

    /**
     * 高吞吐解析流程：生产者-消费者模式 + 虚拟线程 + 批量 COPY。
     * <p>
     * 架构：
     * <pre>
     *   1 个平台线程（StAX Producer）
     *       ↓ 写入
     *   RingBuffer（容量 20K 条记录）
     *       ↓ 读取
     *   4 个虚拟线程（Consumer）
     *       ↓ 批量写入
     *   PostgreSQL COPY 协议（每批 10K 条或 500ms 超时）
     * </pre>
     * <p>
     * 与标准模式的区别：
     * <ul>
     *   <li>解析和写入在不同线程中并行，充分利用 I/O 等待时间</li>
     *   <li>批量 COPY 替代逐条写入，减少数据库交互次数</li>
     *   <li>元数据统计改为 SQL 子查询（从已写入的 health_records 表中聚合）</li>
     * </ul>
     *
     * @param message 解析任务消息
     * @throws Exception 解析或数据库写入失败
     */
    public void processHighThroughput(ParseJobMessage message) throws Exception {
        markParsing(message.uploadId());
        try {
            UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

            long total;
            try (InputStream zipStream = storageService.load(message.storageKey())) {
                // 从 ZIP 中获取 health.xml 的 InputStream
                AppleHealthZipReader.HealthXmlResult result = zipReader.openHealthXmlSinglePass(zipStream);
                // 创建高吞吐管道并执行，返回解析的记录总数
                HighThroughputParsePipeline pipeline = new HighThroughputParsePipeline(
                        dataSource, userId, message.uploadId());
                total = pipeline.execute(result.inputStream());
            }

            // 通过 SQL 子查询从已入库的记录中聚合统计数据，避免在内存中维护
            jdbcTemplate.update(
                    "UPDATE uploads SET status = 'PARSED', record_count = ?, " +
                    "source_count = (SELECT count(DISTINCT source_name) FROM health_records WHERE upload_id = ?), " +
                    "coverage_start_at = (SELECT min(start_at) FROM health_records WHERE upload_id = ?), " +
                    "coverage_end_at = (SELECT max(end_at) FROM health_records WHERE upload_id = ?), " +
                    "finished_at = ? WHERE id = ?",
                    total, message.uploadId(), message.uploadId(), message.uploadId(),
                    Timestamp.from(Instant.now()), message.uploadId());

            // 解析完成，通知 aggregation-worker 开始聚合计算
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
