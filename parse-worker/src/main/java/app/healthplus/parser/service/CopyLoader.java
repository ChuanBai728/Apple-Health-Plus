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

public class CopyLoader implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CopyLoader.class);

    private static final int BUF_SIZE = 8 * 1024 * 1024;

    private final DataSource dataSource;
    private final Path tmpFile;
    private final BufferedOutputStream out;
    private final byte[] uidBytes, upidBytes;
    private final ExecutorService indexExecutor;
    private byte[] lineBuf;
    private int linePos;
    private long rowCount;
    private Instant minStart, maxEnd;

    private static final byte[] BS_NULL = "\\N".getBytes();
    private static final byte COMMA = ',', NEWLINE = '\n', QUOTE = '"';

    public CopyLoader(DataSource dataSource, UUID userId, UUID uploadId) throws IOException {
        this.dataSource = dataSource;
        this.tmpFile = Files.createTempFile("health-copy-", ".csv");
        this.out = new BufferedOutputStream(Files.newOutputStream(tmpFile), BUF_SIZE);
        this.uidBytes  = userId.toString().getBytes(StandardCharsets.UTF_8);
        this.upidBytes = uploadId.toString().getBytes(StandardCharsets.UTF_8);
        this.lineBuf = new byte[1024];
        this.indexExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "copyloader-index-rebuild");
            t.setDaemon(true);
            return t;
        });
    }

    public void writeRecord(String recordType, String metricKey, String categoryKey,
            String sourceName, Double valueNumeric, String valueText,
            String unit, Instant startAt, Instant endAt, String rawPayloadJson) throws IOException {
        linePos = 0;
        writeRaw(uidBytes); writeSep();        // userId — pre-encoded
        writeRaw(upidBytes); writeSep();       // uploadId — pre-encoded
        writeCsv(recordType);
        writeCsv(metricKey);
        writeCsv(categoryKey);
        writeCsv(sourceName);
        if (valueNumeric != null) { writeRaw(doubleToBytes(valueNumeric)); }
        else { writeRaw(BS_NULL); }
        writeSep();
        writeCsv(valueText);
        writeCsv(unit);
        writeRaw(startAt.toString().getBytes(StandardCharsets.UTF_8)); writeSep();
        writeRaw(endAt.toString().getBytes(StandardCharsets.UTF_8));   writeSep();
        writeCsvLast(rawPayloadJson);
        out.write(lineBuf, 0, linePos);
        rowCount++;
        if (minStart == null || startAt.isBefore(minStart)) minStart = startAt;
        if (maxEnd == null || endAt.isAfter(maxEnd)) maxEnd = endAt;
    }

    private void writeSep() { lineBuf[linePos++] = COMMA; }
    private void writeRaw(byte[] b) {
        int len = b.length;
        if (linePos + len > lineBuf.length) grow(linePos + len);
        System.arraycopy(b, 0, lineBuf, linePos, len);
        linePos += len;
    }

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

    private static byte[] doubleToBytes(double d) {
        if (d == (long) d) return Long.toString((long) d).getBytes(StandardCharsets.UTF_8);
        return Double.toString(d).getBytes(StandardCharsets.UTF_8);
    }

    private void ensure(int needed) { if (linePos + needed > lineBuf.length) grow(linePos + needed); }
    private void grow(int min) {
        int n = lineBuf.length * 2; while (n < min) n *= 2;
        byte[] bigger = new byte[n];
        System.arraycopy(lineBuf, 0, bigger, 0, linePos);
        lineBuf = bigger;
    }

    @Override
    public void close() throws IOException {
        out.close();
        long start = System.currentTimeMillis();
        var fileReader = new java.io.FileReader(tmpFile.toFile(), StandardCharsets.UTF_8);
        try (Connection conn = dataSource.getConnection()) {
            var stmt = conn.createStatement();
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_upload_metric_start");
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_user_metric_start");
            stmt.execute("DROP INDEX IF EXISTS idx_health_records_upload_category");

            BaseConnection baseConn = conn.unwrap(BaseConnection.class);
            CopyManager cm = new CopyManager(baseConn);
            long loaded = cm.copyIn(
                    "COPY health_records (user_id, upload_id, record_type, metric_key, category_key, " +
                    "source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json) " +
                    "FROM STDIN WITH (FORMAT csv, NULL '\\N')", fileReader);
            long copyMs = System.currentTimeMillis() - start;
            log.info("COPY loaded {} records in {}ms ({} rows/s)", loaded, copyMs,
                     copyMs > 0 ? loaded * 1000 / copyMs : 0);

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
            try { Files.deleteIfExists(tmpFile); } catch (IOException e) { log.warn("Failed to delete temp file: {}", tmpFile, e); }
        }
    }

    public long getRowCount() { return rowCount; }
    public Instant getMinStart() { return minStart; }
    public Instant getMaxEnd() { return maxEnd; }
}
