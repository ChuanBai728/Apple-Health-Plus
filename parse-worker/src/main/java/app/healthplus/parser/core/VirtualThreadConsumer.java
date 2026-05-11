package app.healthplus.parser.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于虚拟线程的消费者，负责将原始 XML 记录解析为结构化的
 * {@link PgCopyBatchInserter.ParsedRecord} 并喂入 {@link PgCopyBatchInserter}。
 * <p>
 * 设计思路：
 * <ol>
 *   <li>使用 {@link Executors#newVirtualThreadPerTaskExecutor()} 为每条记录分配一个虚拟线程，
 *       实现海量并发而不会耗尽操作系统线程资源（虚拟线程由 JVM 调度，内存开销仅约几 KB）。</li>
 *   <li>从 {@link RecordRingBuffer} 无锁环形缓冲区中拉取原始记录，
 *       解析后通过 {@link PgCopyBatchInserter#append} 写入数据库。</li>
 *   <li>内置指标归一化逻辑（如百分比小数→百分数、睡眠时长计算），
 *       确保写入数据库的数值符合业务语义。</li>
 * </ol>
 *
 * 在数据处理流水线中的位置：
 * <pre>
 *   [XML StAX 解析器] → RecordRingBuffer → [VirtualThreadConsumer] → PgCopyBatchInserter → PostgreSQL
 * </pre>
 * 本类处于流水线中间层，承上（从缓冲区读取）启下（结构化后写入数据库）。
 *
 * @see RecordRingBuffer       上游：无锁环形缓冲区，存放原始 XML 记录
 * @see PgCopyBatchInserter    下游：高性能批量写入器
 */
public class VirtualThreadConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConsumer.class);

    /**
     * 指标键名 → 分类键名的映射表。
     * <p>
     * 用于将 Apple Health 的细粒度指标（如 heart_rate）归类到业务分类（如 heart_cardio），
     * 支持前端按分类聚合展示。使用不可变 Map，线程安全且无同步开销。
     * 未命中的指标默认归入 "other" 分类。
     */
    private static final Map<String, String> CATEGORY_MAP = Map.ofEntries(
            Map.entry("heart_rate", "heart_cardio"),               // 心率 → 心血管
            Map.entry("resting_heart_rate", "heart_cardio"),       // 静息心率 → 心血管
            Map.entry("heart_rate_variability_sdnn", "heart_cardio"), // 心率变异性 → 心血管
            Map.entry("body_mass", "body_metrics"),                // 体重 → 身体指标
            Map.entry("body_mass_index", "body_metrics"),          // BMI → 身体指标
            Map.entry("step_count", "daily_activity"),             // 步数 → 日常活动
            Map.entry("active_energy_burned", "daily_activity"),   // 活动消耗 → 日常活动
            Map.entry("basal_energy_burned", "daily_activity"),    // 基础代谢 → 日常活动
            Map.entry("distance_walking_running", "daily_activity"), // 步行/跑步距离 → 日常活动
            Map.entry("sleep_analysis", "sleep_recovery"),         // 睡眠分析 → 睡眠与恢复
            Map.entry("oxygen_saturation", "vitals_respiratory"),  // 血氧 → 生命体征/呼吸
            Map.entry("respiratory_rate", "vitals_respiratory"),   // 呼吸频率 → 生命体征/呼吸
            Map.entry("mindful_session", "mindfulness_mental"),    // 正念 → 心理健康
            Map.entry("workout", "workouts_training")              // 锻炼 → 运动训练
    );

    /** 上游数据源：无锁环形缓冲区，存放 StAX 解析器产出的原始记录 */
    private final RecordRingBuffer buffer;
    /** 下游写入器：高性能 PostgreSQL COPY 批量插入器 */
    private final PgCopyBatchInserter inserter;
    /** 已成功处理并写入的记录计数，用于统计和日志 */
    private final AtomicLong processedCount = new AtomicLong(0);
    /** 运行标志，外部可通过 {@link #shutdown()} 设为 false 以优雅停止 */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /**
     * 构造虚拟线程消费者。
     *
     * @param buffer   上游环形缓冲区
     * @param inserter 下游批量写入器
     */
    public VirtualThreadConsumer(RecordRingBuffer buffer, PgCopyBatchInserter inserter) {
        this.buffer = buffer;
        this.inserter = inserter;
    }

    /**
     * 消费者主循环：持续从环形缓冲区拉取原始记录，提交到虚拟线程池进行解析和写入。
     * <p>
     * 生命周期：
     * <ol>
     *   <li>循环调用 {@link RecordRingBuffer#poll(long)} 获取原始记录</li>
     *   <li>每条记录提交到虚拟线程池，执行 {@link #processRecord} 解析 + {@link PgCopyBatchInserter#append} 写入</li>
     *   <li>当缓冲区已排空（{@code isDrained()}）且无更多数据时退出循环</li>
     *   <li>等待所有在途任务完成后，执行最终的 {@link PgCopyBatchInserter#flushRemaining()}</li>
     * </ol>
     * 使用 try-with-resources 管理虚拟线程池，确保 {@code close()} 时所有任务被等待完成。
     */
    @Override
    public void run() {
        // newVirtualThreadPerTaskExecutor: 每个 submit 的任务都会在新的虚拟线程上执行
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (running.get()) {
                // 轮询缓冲区，超时 100ms 后返回 null
                RecordRingBuffer.RawRecord raw = buffer.poll(100);
                if (raw == null) {
                    // 缓冲区已排空且生产者已结束，退出循环
                    if (buffer.isDrained()) break;
                    continue;
                }

                // 将记录解析和写入提交到虚拟线程——解析包含字符串处理，属于 CPU 密集型操作
                executor.submit(() -> {
                    try {
                        PgCopyBatchInserter.ParsedRecord parsed = processRecord(raw);
                        inserter.append(parsed);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Failed to process record: {}", raw.type(), e);
                    }
                });
            }

            // 关闭线程池：不再接受新任务，等待所有在途任务完成
            executor.close();
            executor.awaitTermination(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            // 恢复中断标志，遵守 Java 中断协议
            Thread.currentThread().interrupt();
            log.warn("Consumer interrupted");
        } catch (Exception e) {
            log.error("Consumer error", e);
        } finally {
            // 安全刷新：确保不满一批的尾部记录也被写入数据库
            inserter.flushRemaining();
            log.info("Consumer finished: {} records processed", processedCount.get());
        }
    }

    /**
     * 请求优雅停止：设置运行标志为 false，当前循环迭代完成后退出。
     * <p>
     * 注意：此方法不会中断正在处理的虚拟线程，仅阻止拉取新记录。
     * 若缓冲区中仍有数据，已提交到线程池的任务会继续执行完毕。
     */
    public void shutdown() {
        running.set(false);
    }

    /**
     * 返回已成功处理并写入数据库的记录总数。
     *
     * @return 已处理记录数
     */
    public long processedCount() {
        return processedCount.get();
    }

    // ── 记录解析核心逻辑 ──

    /**
     * 将原始 XML 记录解析为可写入数据库的 {@link PgCopyBatchInserter.ParsedRecord}。
     * <p>
     * 解析流程：
     * <ol>
     *   <li>根据原始类型（HKWorkout* / HK*）判断记录类型并提取 metricKey</li>
     *   <li>通过 {@link #CATEGORY_MAP} 映射到业务分类</li>
     *   <li>解析数值、归一化单位（如百分比指标 ×100 转为百分数）</li>
     *   <li>特殊处理睡眠分析：从分类值（Asleep/InBed）转换为时长（小时）</li>
     *   <li>组装轻量级 JSON 元数据用于审计</li>
     * </ol>
     * 此方法为 CPU 密集型操作（字符串解析/转换），在虚拟线程中执行。
     *
     * @param raw 从环形缓冲区取出的原始记录
     * @return 解析完成的结构化记录，可直接用于 COPY 写入
     */
    static PgCopyBatchInserter.ParsedRecord processRecord(RecordRingBuffer.RawRecord raw) {
        String localName = "Record";
        // 通过类型前缀区分锻炼记录和普通健康记录
        boolean isWorkout = raw.type().startsWith("HKWorkout");

        // 将 Apple 冗长的类型标识符（如 HKQuantityTypeIdentifierHeartRate）转为简洁的 metric_key（heart_rate）
        String metricKey = normalizeMetricKey(raw.type(), isWorkout ? "Workout" : "Record");
        // 锻炼记录直接归入 workouts_training，普通记录查映射表
        String categoryKey = isWorkout ? "workouts_training"
                : CATEGORY_MAP.getOrDefault(metricKey, "other");

        String value = isWorkout ? raw.value() : raw.value(); // 生产者已设置原始值
        Double valueNumeric = parseDouble(value);
        // 归一化单位：如 body_fat_percentage 的单位统一为 "%"
        String unit = isWorkout ? raw.unit() : normalizeUnit(metricKey, raw.unit());
        Instant startAt = parseInstant(raw.startDate());
        Instant endAt = parseInstant(raw.endDate());

        // 睡眠分析特殊处理：Apple Health 的睡眠数据是分类值（Asleep/InBed/Awake），
        // 没有直接的数值。我们用 startAt 和 endAt 的差值计算睡眠时长（小时）。
        if ("HKCategoryTypeIdentifierSleepAnalysis".equals(raw.type())) {
            metricKey = "sleep_duration";
            valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
            unit = "h";
        }

        // 归一化数值：百分比类指标从小数（0.95）转为百分数（95.0）
        valueNumeric = normalizeValue(metricKey, valueNumeric);

        // 轻量级 JSON 元数据，仅保留 sourceName 和 metricKey 用于审计
        String json = "{\"sourceName\":\"" + escapeJson(raw.sourceName())
                + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

        return new PgCopyBatchInserter.ParsedRecord(
                isWorkout ? "workout" : "record",
                metricKey,
                categoryKey,
                raw.sourceName(),
                valueNumeric,
                value,
                unit,
                startAt,
                endAt,
                json
        );
    }

    // ── 辅助方法：镜像 AppleHealthParser 的归一化逻辑 ──

    /**
     * 将 Apple Health 的冗长类型标识符转为简洁的下划线命名 metricKey。
     * <p>
     * 转换规则：
     * <ul>
     *   <li>移除常见前缀：HKQuantityTypeIdentifier / HKCategoryTypeIdentifier / HKWorkoutActivityType</li>
     *   <li>驼峰命名转下划线命名（camelCase → camel_case）</li>
     *   <li>统一转小写</li>
     * </ul>
     * 示例：{@code "HKQuantityTypeIdentifierHeartRate" → "heart_rate"}
     *
     * @param type      Apple Health 原始类型标识符
     * @param localName XML 本地名称（备用，当 type 为空时使用）
     * @return 规范化的 metricKey
     */
    private static String normalizeMetricKey(String type, String localName) {
        if (type == null || type.isBlank()) return localName.toLowerCase(Locale.ROOT);
        return type.replace("HKQuantityTypeIdentifier", "")
                .replace("HKCategoryTypeIdentifier", "")
                .replace("HKWorkoutActivityType", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2") // 驼峰 → 下划线
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 安全地将字符串解析为 Double，解析失败返回 null。
     *
     * @param value 待解析的字符串
     * @return 解析后的数值，解析失败返回 null
     */
    private static Double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return null; }
    }

    /**
     * 归一化数值：将百分比类指标从小数形式（0.95）转为百分数形式（95.0）。
     * <p>
     * Apple Health 中血氧饱和度、体脂率等指标以 0-1 小数存储，
     * 为便于用户理解和前端展示，统一乘以 100 转为百分数。
     *
     * @param metricKey 指标键名
     * @param value     原始数值
     * @return 归一化后的数值
     */
    private static Double normalizeValue(String metricKey, Double value) {
        if (value == null) return null;
        return switch (metricKey) {
            // 这些指标在 Apple Health 中以 0-1 小数存储，需转为 0-100 百分数
            case "body_fat_percentage", "oxygen_saturation",
                 "apple_walking_steadiness", "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> value * 100.0;
            default -> value;
        };
    }

    /**
     * 归一化单位：为百分比类指标统一使用 "%"，体重统一使用 "kg"。
     *
     * @param metricKey 指标键名
     * @param unit      原始单位
     * @return 归一化后的单位
     */
    private static String normalizeUnit(String metricKey, String unit) {
        if (unit == null) return "";
        return switch (metricKey) {
            // 百分比类指标统一单位为 "%"
            case "body_fat_percentage", "oxygen_saturation",
                 "apple_walking_steadiness", "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> "%";
            case "body_mass" -> "kg"; // 体重统一使用公制单位
            default -> unit;
        };
    }

    /**
     * 安全地将时间字符串解析为 {@link Instant}。
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>ISO-8601 带 T 格式：如 "2024-01-15T08:30:00+08:00"</li>
     *   <li>Apple Health 专有格式：如 "2024-01-15 08:30:00 +0800"（无冒号的时区偏移）</li>
     * </ul>
     * Apple Health 的时区偏移格式需要先补上冒号（+0800 → +08:00）才能被 Java 解析。
     * 解析失败返回 {@link Instant#EPOCH} 作为哨兵值。
     *
     * @param value 时间字符串
     * @return 解析后的 Instant，失败返回 Instant.EPOCH
     */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH;
        // 已经是标准 ISO-8601 格式，直接解析
        if (value.contains("T")) {
            try { return Instant.parse(value.trim()); }
            catch (Exception e) { return Instant.EPOCH; }
        }
        // Apple Health 格式："2024-01-15 08:30:00 +0800"
        // 需要转换为 OffsetDateTime 可解析的格式
        try {
            String normalized = value.trim()
                    .replaceFirst(" ([+-])(\\d{2})(\\d{2})$", "$1$2:$3") // +0800 → +08:00
                    .replace(" ", "T")  // 空格替换为 T，变为 ISO 格式
                    .replace(" ", "");  // 清除残余空格
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    /**
     * JSON 字符串转义：处理反斜杠和双引号。
     * <p>
     * 用于构建 rawPayloadJson 字段，避免注入破坏 JSON 结构。
     * 不做完整 JSON 转义（如 Unicode 控制字符），因为设备名和 metricKey 通常不含这些字符。
     *
     * @param value 待转义的字符串
     * @return 转义后的字符串
     */
    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
