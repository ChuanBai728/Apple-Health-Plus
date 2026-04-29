package app.healthplus.parser.core;

import com.fasterxml.aalto.stax.InputFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * StAX-based streaming XML producer.
 * Scans {@code <Record>} elements, extracts raw attributes,
 * and pushes them into the {@link RecordRingBuffer}.
 * <p>
 * Runs on a single platform thread — minimal CPU, zero memory overhead.
 * Backpressure: blocks on {@code buffer.put()} when consumers are slow.
 */
public class XmlRecordProducer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(XmlRecordProducer.class);

    private final InputStream inputStream;
    private final RecordRingBuffer buffer;

    public XmlRecordProducer(InputStream inputStream, RecordRingBuffer buffer) {
        this.inputStream = inputStream;
        this.buffer = buffer;
    }

    @Override
    public void run() {
        XMLInputFactory factory = new InputFactoryImpl();
        long count = 0;
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                    String localName = reader.getLocalName();
                    if (!"Record".equals(localName) && !"Workout".equals(localName)) continue;

                    String type = attr(reader, "type");
                    String value = "Workout".equals(localName)
                            ? attr(reader, "duration")
                            : attr(reader, "value");
                    RecordRingBuffer.RawRecord rec = new RecordRingBuffer.RawRecord(
                            type,
                            value,
                            "Workout".equals(localName) ? attr(reader, "durationUnit") : attr(reader, "unit"),
                            attr(reader, "sourceName"),
                            attr(reader, "startDate"),
                            attr(reader, "endDate")
                    );

                    buffer.put(rec); // blocks if queue full — backpressure
                    count++;
                }
            } finally {
                reader.close();
            }
            log.info("Producer finished: {} records parsed", count);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Producer interrupted after {} records", count);
        } catch (Exception e) {
            log.error("Producer error after {} records", count, e);
            buffer.setProducerError(e);
        } finally {
            buffer.markProducerDone();
        }
    }

    private static String attr(XMLStreamReader reader, String name) {
        String v = reader.getAttributeValue(null, name);
        return v == null ? "" : v;
    }
}
