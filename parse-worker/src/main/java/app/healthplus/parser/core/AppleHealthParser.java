package app.healthplus.parser.core;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Apple Health XML 核心解析器。
 *
 * <h2>职责</h2>
 * <p>
 * 将 Apple Health 导出的 {@code export.xml} 中 {@code <Record>} 和 {@code <Workout>} 元素
 * 解析为统一的 {@link ParsedHealthRecord} 对象。执行以下标准化工作：
 * <ul>
 *   <li>将 Apple 的 HK 类型标识符（如 {@code HKQuantityTypeIdentifierHeartRate}）转换为
 *       下划线风格的 metricKey（如 {@code heart_rate}）</li>
 *   <li>将指标归类到 12 个业务分类（心脏、身体指标、日常活动等）</li>
 *   <li>将 Apple 以 0-1 小数存储的百分比指标（血氧、体脂率等）放大到 0-100</li>
 *   <li>将睡眠分析的分类记录转换为以小时为单位的数值型 sleep_duration</li>
 * </ul>
 *
 * <h2>设计思路</h2>
 * <p>
 * 使用 StAX（Streaming API for XML）逐事件读取 XML，而非 DOM 一次性加载——
 * Apple Health 导出文件通常包含 200 万条以上记录，DOM 方式会导致 OOM。
 * 底层采用 Aalto XML 解析器（{@code com.fasterxml.aalto}），比 JDK 内置实现快 3-5 倍。
 *
 * <h2>在流水线中的位置</h2>
 * <pre>
 * export.zip → ZipInputStream → AppleHealthParser → ParsedHealthRecord → COPY 批量入库
 * </pre>
 * <p>
 * 本类提供两种解析模式：
 * <ul>
 *   <li><b>流式模式</b>（{@link #parseXmlStreaming}）：逐条回调，零中间列表，内存占用最低</li>
 *   <li><b>收集模式</b>（{@link #parseXml}）：返回完整列表，适用于小文件或测试场景</li>
 * </ul>
 *
 * @see ParsedHealthRecord 解析结果记录
 * @see XmlRecordProducer 基于 RingBuffer 的生产者-消费者模式解析器（另一条流水线）
 */
@Component
public class AppleHealthParser {

    /**
     * Apple Health 指标类型 → 业务分类映射表。
     * <p>
     * 映射关系基于以下原则：
     * <ul>
     *   <li>同一器官系统或功能域的指标归入同一分类</li>
     *   <li>无法匹配的指标归入 {@code "other"} 分类</li>
     *   <li>Workout 类型直接由 {@link #categoryKey} 方法硬编码为 {@code "workouts_training"}</li>
     * </ul>
     */
    private static final Map<String, String> CATEGORY_MAP = Map.ofEntries(
            Map.entry("heart_rate", "heart_cardio"),
            Map.entry("resting_heart_rate", "heart_cardio"),
            Map.entry("heart_rate_variability_sdnn", "heart_cardio"),
            Map.entry("body_mass", "body_metrics"),
            Map.entry("body_mass_index", "body_metrics"),
            Map.entry("step_count", "daily_activity"),
            Map.entry("active_energy_burned", "daily_activity"),
            Map.entry("basal_energy_burned", "daily_activity"),
            Map.entry("distance_walking_running", "daily_activity"),
            Map.entry("sleep_analysis", "sleep_recovery"),
            Map.entry("oxygen_saturation", "vitals_respiratory"),
            Map.entry("respiratory_rate", "vitals_respiratory"),
            Map.entry("mindful_session", "mindfulness_mental"),
            Map.entry("workout", "workouts_training")
    );

    /**
     * 流式解析 XML：逐条将 {@code <Record>} / {@code <Workout>} 元素转换为
     * {@link ParsedHealthRecord} 并通过 consumer 回调，不构建中间列表。
     *
     * <p>设计目的：Apple Health 导出文件通常含 200 万条以上记录，全部加载到 List 会占用
     * 数 GB 堆内存。流式模式下每解析一条立即消费，堆上同一时刻只存在一条记录。</p>
     *
     * @param inputStream 输入流（通常来自 ZipInputStream 解压 export.xml）
     * @param consumer    每条记录的回调处理函数（通常用于批量 COPY 入库）
     * @return 成功解析并传递给 consumer 的记录总数
     * @throws XMLStreamException XML 格式错误或解析中断
     * @throws IOException        I/O 读取异常
     */
    public long parseXmlStreaming(InputStream inputStream, Consumer<ParsedHealthRecord> consumer)
            throws XMLStreamException, IOException {
        // 使用 Aalto XML 解析器，比 JDK 内置 StAX 快 3-5 倍，适合 GB 级 XML
        XMLInputFactory factory = new com.fasterxml.aalto.stax.InputFactoryImpl();
        XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
        long count = 0;
        try {
            while (reader.hasNext()) {
                // 跳过非开始元素事件（如 END_ELEMENT、CHARACTERS、COMMENT 等）
                if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                String localName = reader.getLocalName();
                // 只处理 Record（含 HKQuantity/HKCategory 类型）和 Workout 两种元素
                if (!"Record".equals(localName) && !"Workout".equals(localName)) continue;

                // 将 Apple HK 类型标识符转为下划线风格的 metricKey
                String metricKey = normalizeMetricKey(attribute(reader, "type"), localName);
                // 根据 metricKey 查找业务分类（心脏、活动、睡眠等）
                String categoryKey = categoryKey(metricKey, localName);
                String sourceName = attribute(reader, "sourceName");
                // Workout 用 duration 属性作为值，Record 用 value 属性
                String value = "Workout".equals(localName) ? attribute(reader, "duration") : attribute(reader, "value");
                // 百分比类指标（血氧、体脂等）从 0-1 放大到 0-100
                Double valueNumeric = normalizeValue(metricKey, parseDouble(value));
                String unit = "Workout".equals(localName) ? attribute(reader, "durationUnit") : normalizeUnit(metricKey, attribute(reader, "unit"));
                Instant startAt = parseInstant(attribute(reader, "startDate"));
                Instant endAt = parseInstant(attribute(reader, "endDate"));
                // 构造精简的 JSON 快照，仅保留 sourceName 和 metricKey 用于溯源
                String rawPayloadJson = "{\"sourceName\":\"" + escapeJson(sourceName) + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

                // 睡眠分析在 Apple Health 中是分类数据（如 asleep/inBed/awake），
                // 转换为 sleep_duration 数值（小时），方便聚合和图表展示
                String type = attribute(reader, "type");
                if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
                    metricKey = "sleep_duration";
                    // 用 endDate - startDate 计算睡眠时段时长，毫秒转小时
                    valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
                    unit = "h";
                }

                consumer.accept(new ParsedHealthRecord(
                        localName.toLowerCase(Locale.ROOT), metricKey, categoryKey,
                        sourceName, valueNumeric, value, unit, startAt, endAt, rawPayloadJson));
                count++;
            }
        } finally {
            reader.close();
        }
        return count;
    }

    /**
     * 收集模式解析 XML：将所有 {@code <Record>} / {@code <Workout>} 元素解析后
     * 收集到一个 List 中返回。
     *
     * <p>适用于小文件、单元测试或需要一次性访问全部记录的场景。
     * 生产环境推荐使用 {@link #parseXmlStreaming} 流式模式以降低内存压力。</p>
     *
     * @param inputStream 输入流（通常来自 ZipInputStream 解压 export.xml）
     * @return 包含所有已解析记录的列表，不会为 null
     * @throws XMLStreamException XML 格式错误或解析中断
     * @throws IOException        I/O 读取异常
     */
    public List<ParsedHealthRecord> parseXml(InputStream inputStream) throws XMLStreamException, IOException {
        // 使用 Aalto XML 解析器，比 JDK 内置 StAX 快 3-5 倍
        XMLInputFactory factory = new com.fasterxml.aalto.stax.InputFactoryImpl();
        XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
        List<ParsedHealthRecord> results = new ArrayList<>();

        try {
            while (reader.hasNext()) {
                // 跳过非开始元素事件
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }

                String localName = reader.getLocalName();
                // 只处理 Record 和 Workout 两种元素
                if (!"Record".equals(localName) && !"Workout".equals(localName)) {
                    continue;
                }

                // 标准化指标键和业务分类
                String metricKey = normalizeMetricKey(attribute(reader, "type"), localName);
                String categoryKey = categoryKey(metricKey, localName);
                String sourceName = attribute(reader, "sourceName");
                // Workout 用 duration，Record 用 value
                String value = "Workout".equals(localName) ? attribute(reader, "duration") : attribute(reader, "value");
                Double valueNumeric = normalizeValue(metricKey, parseDouble(value));
                String unit = "Workout".equals(localName) ? attribute(reader, "durationUnit") : normalizeUnit(metricKey, attribute(reader, "unit"));
                Instant startAt = parseInstant(attribute(reader, "startDate"));
                Instant endAt = parseInstant(attribute(reader, "endDate"));
                // 精简 JSON 快照，仅保留溯源所需字段
                String rawPayloadJson = "{\"sourceName\":\"" + escapeJson(sourceName) + "\",\"metricKey\":\"" + escapeJson(metricKey) + "\"}";

                // 睡眠分析：分类数据 → 数值型时长（小时）
                String type = attribute(reader, "type");
                if ("HKCategoryTypeIdentifierSleepAnalysis".equals(type)) {
                    metricKey = "sleep_duration";
                    valueNumeric = (endAt.toEpochMilli() - startAt.toEpochMilli()) / 3600000.0;
                    unit = "h";
                }

                results.add(new ParsedHealthRecord(
                        localName.toLowerCase(Locale.ROOT),
                        metricKey,
                        categoryKey,
                        sourceName,
                        valueNumeric,
                        value,
                        unit,
                        startAt,
                        endAt,
                        rawPayloadJson
                ));
            }
        } finally {
            reader.close();
        }

        return results;
    }

    /**
     * 安全读取 XML 元素属性，属性不存在时返回空字符串而非 null。
     * 这样下游代码无需反复做 null 检查，简化调用链。
     *
     * @param reader StAX 读取器，当前游标必须位于 START_ELEMENT
     * @param name   属性名（如 "type"、"value"、"startDate"）
     * @return 属性值字符串，不存在则返回 ""
     */
    private String attribute(XMLStreamReader reader, String name) {
        String value = reader.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    /**
     * 将 Apple Health 的 HK 类型标识符标准化为下划线风格的 metricKey。
     *
     * <p>转换规则：去除前缀（HKQuantityTypeIdentifier / HKCategoryTypeIdentifier / HKWorkoutActivityType），
     * 将驼峰命名拆分为下划线命名并转小写。</p>
     *
     * <p>示例：{@code "HKQuantityTypeIdentifierHeartRateVariabilitySDNN"} → {@code "heart_rate_variability_sdnn"}</p>
     *
     * @param type      Apple Health 的 type 属性值（如 "HKQuantityTypeIdentifierHeartRate"）
     * @param localName XML 元素本地名（"Record" 或 "Workout"），当 type 为空时作为兜底
     * @return 标准化后的 metricKey（如 "heart_rate"、"workout"）
     */
    private String normalizeMetricKey(String type, String localName) {
        // type 为空时，用元素名兜底（如 Workout 元素可能没有 type 属性）
        if (type == null || type.isBlank()) {
            return localName.toLowerCase(Locale.ROOT);
        }
        // 依次移除三种 HK 前缀，再将驼峰转下划线
        return type.replace("HKQuantityTypeIdentifier", "")
                .replace("HKCategoryTypeIdentifier", "")
                .replace("HKWorkoutActivityType", "")
                .replaceAll("([a-z])([A-Z])", "$1_$2")  // 驼峰边界插入下划线
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 根据 metricKey 查找对应的业务分类。
     *
     * <p>Workout 元素硬编码为 {@code "workouts_training"}；
     * Record 元素通过 {@link #CATEGORY_MAP} 映射，未命中则归入 {@code "other"}。</p>
     *
     * @param metricKey 标准化后的指标键（如 "heart_rate"）
     * @param localName XML 元素本地名，用于判断是否为 Workout
     * @return 业务分类键（如 "heart_cardio"、"daily_activity"）
     */
    private String categoryKey(String metricKey, String localName) {
        if ("Workout".equals(localName)) {
            return "workouts_training";
        }
        return CATEGORY_MAP.getOrDefault(metricKey, "other");
    }

    /**
     * 将 Apple Health 的日期时间字符串解析为 UTC Instant。
     *
     * <p>Apple Health 导出文件存在两种日期格式：
     * <ul>
     *   <li>ISO 8601：{@code "2026-04-26T10:00:00Z"} — 直接用 {@link Instant#parse} 解析</li>
     *   <li>Apple 真实导出格式：{@code "2024-01-15 08:30:00 +0800"} — 空格分隔、时区无冒号，
     *       需先标准化为 ISO 格式再通过 {@link OffsetDateTime} 解析</li>
     * </ul>
     * 解析失败时返回 {@link Instant#EPOCH}（1970-01-01T00:00:00Z）作为安全兜底，
     * 避免一条脏数据导致整个导入任务失败。</p>
     *
     * @param value 原始日期时间字符串
     * @return 解析后的 UTC Instant，失败返回 Instant.EPOCH
     */
    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return Instant.EPOCH;
        // 优先处理 ISO 格式：含 "T" 说明已经是标准格式
        if (value.contains("T")) {
            try { return Instant.parse(value.trim()); }
            catch (Exception e) { return Instant.EPOCH; }
        }
        // Apple Health 真实导出格式："2024-01-15 08:30:00 +0800"
        try {
            String normalized = value.trim()
                    .replaceFirst(" ([+-])(\\d{2})(\\d{2})$", "$1$2:$3") // 时区 "+0800" → "+08:00"，符合 ISO 8601
                    .replace(" ", "T")   // 日期与时间之间的空格替换为 T
                    .replace(" ", "");   // 清除剩余空格（时区冒号插入后可能残留）
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    /**
     * 安全地将字符串解析为 Double，解析失败返回 null。
     * Apple Health 中部分 Record 的 value 属性为空或非数字文本（如睡眠分析的分类值），
     * 此时返回 null，由下游的 {@link #normalizeValue} 处理。
     *
     * @param value 原始字符串值
     * @return 解析后的 Double，失败返回 null
     */
    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 标准化指标数值：将 Apple 以 0-1 小数存储的百分比指标放大到 0-100。
     *
     * <p>Apple Health 内部以 0.0-1.0 的小数存储百分比类指标（如血氧 0.98 表示 98%），
     * 但用户期望看到 0-100 的百分比值。需要做此转换的指标包括：
     * 体脂率、血氧饱和度、步行稳定性、步态不对称率、双脚支撑时间比。</p>
     *
     * @param metricKey 标准化后的指标键
     * @param value     原始数值（可能为 null）
     * @return 标准化后的数值，百分比类指标已乘以 100；null 输入返回 null
     */
    private Double normalizeValue(String metricKey, Double value) {
        if (value == null) return null;
        return switch (metricKey) {
            // 这些指标 Apple 内部以 0-1 存储，需放大 100 倍变为百分比
            case "body_fat_percentage",
                 "oxygen_saturation",
                 "apple_walking_steadiness",
                 "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> value * 100.0;
            case "body_mass" -> value;  // 单位已是 kg，无需转换
            default -> value;
        };
    }

    /**
     * 标准化指标单位：与 {@link #normalizeValue} 配合，确保百分比类指标的单位统一为 "%"。
     *
     * <p>Apple Health 原始 XML 中这些指标的单位可能为空或 "count"，
     * 但经过数值放大后，语义上应标记为 "%"。体重统一标记为 "kg"。</p>
     *
     * @param metricKey 标准化后的指标键
     * @param unit      XML 中的原始单位属性值
     * @return 标准化后的单位字符串
     */
    private String normalizeUnit(String metricKey, String unit) {
        if (unit == null) return "";
        return switch (metricKey) {
            // 百分比类指标统一使用 "%"，覆盖 Apple 原始 XML 中可能为空或 "count" 的情况
            case "body_fat_percentage",
                 "oxygen_saturation",
                 "apple_walking_steadiness",
                 "walking_asymmetry_percentage",
                 "walking_double_support_percentage" -> "%";
            case "body_mass" -> "kg";
            default -> unit;
        };
    }

    /**
     * 对字符串进行 JSON 转义，防止特殊字符破坏 JSON 结构。
     * 仅处理反斜杠和双引号——这是 rawPayloadJson 构造中唯一需要的转义。
     *
     * @param value 原始字符串（可能为 null）
     * @return 转义后的安全字符串，null 输入返回 ""
     */
    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
