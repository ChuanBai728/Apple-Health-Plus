package app.healthplus.parser.core;

import com.fasterxml.aalto.stax.InputFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * 基于 StAX 的流式 XML 生产者——生产者-消费者模式中的"生产者"角色。
 *
 * <h2>职责</h2>
 * <p>
 * 逐事件扫描 {@code export.xml} 中的 {@code <Record>} 和 {@code <Workout>} 元素，
 * 提取原始 XML 属性后封装为轻量级的 {@link RecordRingBuffer.RawRecord}，
 * 并推入有界 {@link RecordRingBuffer} 环形缓冲区。
 * </p>
 *
 * <h2>设计思路</h2>
 * <ul>
 *   <li><b>单线程运行</b>：在单个平台线程上执行，CPU 开销极小，无额外内存分配</li>
 *   <li><b>背压控制</b>：当消费者处理速度跟不上时，{@code buffer.put()} 会阻塞生产者，
 *       防止 GB 级 XML 导致 OOM</li>
 *   <li><b>轻量记录</b>：只提取原始属性字符串（{@link RecordRingBuffer.RawRecord}），
 *       不做类型转换或标准化——这些工作交给消费者线程并行完成</li>
 * </ul>
 *
 * <h2>在流水线中的位置</h2>
 * <pre>
 * export.xml → XmlRecordProducer → RecordRingBuffer → [消费者线程池] → AppleHealthParser → DB
 * </pre>
 * <p>
 * 与 {@link AppleHealthParser} 的区别：本类只负责"搬运"原始属性到缓冲区，
 * 不做 metricKey 标准化、分类映射、单位转换等业务逻辑——
 * 这些在消费者线程中通过 {@link AppleHealthParser} 完成，从而实现解析与 I/O 的并行化。
 * </p>
 *
 * @see RecordRingBuffer      有界环形缓冲区（背压机制）
 * @see AppleHealthParser     消费者端的业务解析器
 */
public class XmlRecordProducer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(XmlRecordProducer.class);

    /** 输入流，通常来自 ZipInputStream 解压 export.xml 后得到的流 */
    private final InputStream inputStream;

    /** 有界环形缓冲区，生产者与消费者之间的数据桥梁 */
    private final RecordRingBuffer buffer;

    /**
     * 构造生产者实例。
     *
     * @param inputStream XML 输入流（通常来自 ZipInputStream）
     * @param buffer      有界环形缓冲区，生产者往里写、消费者从中读
     */
    public XmlRecordProducer(InputStream inputStream, RecordRingBuffer buffer) {
        this.inputStream = inputStream;
        this.buffer = buffer;
    }

    /**
     * 生产者主循环：逐事件读取 XML，提取 Record/Workout 属性后推入缓冲区。
     *
     * <p>生命周期管理：
     * <ul>
     *   <li>正常结束：日志记录总数，标记 {@code producerDone}</li>
     *   <li>中断（InterruptedException）：恢复中断标志，标记 done</li>
     *   <li>其他异常：记录错误到缓冲区，标记 done——消费者可通过
     *       {@code buffer.getProducerError()} 感知并终止</li>
     * </ul>
     * 无论何种退出路径，{@code finally} 块都会调用 {@code markProducerDone()}，
     * 确保消费者不会无限等待。</p>
     */
    @Override
    public void run() {
        // 使用 Aalto XML 解析器，比 JDK 内置 StAX 快 3-5 倍
        XMLInputFactory factory = new InputFactoryImpl();
        long count = 0;
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            try {
                while (reader.hasNext()) {
                    // 跳过非开始元素事件（END_ELEMENT、CHARACTERS、COMMENT 等）
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                    String localName = reader.getLocalName();
                    // 只处理 Record 和 Workout，其余元素（如 MetadataEntry、ActivitySummary）全部跳过
                    if (!"Record".equals(localName) && !"Workout".equals(localName)) continue;

                    // 提取原始属性——不做任何标准化，保持轻量
                    String type = attr(reader, "type");
                    // Workout 用 duration 作为值，Record 用 value
                    String value = "Workout".equals(localName)
                            ? attr(reader, "duration")
                            : attr(reader, "value");
                    // 封装为轻量 RawRecord，仅含原始字符串，无对象分配开销
                    RecordRingBuffer.RawRecord rec = new RecordRingBuffer.RawRecord(
                            type,
                            value,
                            "Workout".equals(localName) ? attr(reader, "durationUnit") : attr(reader, "unit"),
                            attr(reader, "sourceName"),
                            attr(reader, "startDate"),
                            attr(reader, "endDate")
                    );

                    // 阻塞式放入——当缓冲区满时（消费者处理慢），生产者在此等待
                    // 这就是背压机制：防止 GB 级 XML 导致 OOM
                    buffer.put(rec);
                    count++;
                }
            } finally {
                reader.close();
            }
            log.info("Producer finished: {} records parsed", count);
        } catch (InterruptedException e) {
            // 线程被中断（如应用关闭），恢复中断标志以便上层感知
            Thread.currentThread().interrupt();
            log.warn("Producer interrupted after {} records", count);
        } catch (Exception e) {
            // 解析异常（如 XML 格式错误），记录到缓冲区供消费者检查
            log.error("Producer error after {} records", count, e);
            buffer.setProducerError(e);
        } finally {
            // 无论正常结束还是异常退出，都标记生产者完成
            // 消费者通过 isDrained() 或 poll() 返回 null 感知到这一点后退出
            buffer.markProducerDone();
        }
    }

    /**
     * 安全读取 XML 元素属性，属性不存在时返回空字符串而非 null。
     * 与 {@link AppleHealthParser#attribute} 逻辑一致，但作为 static 方法
     * 便于在本类内部直接调用。
     *
     * @param reader StAX 读取器，当前游标位于 START_ELEMENT
     * @param name   属性名
     * @return 属性值，不存在返回 ""
     */
    private static String attr(XMLStreamReader reader, String name) {
        String v = reader.getAttributeValue(null, name);
        return v == null ? "" : v;
    }
}
