package app.healthplus.api.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEMO_UPLOAD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final String DEMO_STORAGE_KEY = "demo/demo-export.zip";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM uploads WHERE id = ?",
                    String.class, DEMO_UPLOAD_ID);
            if ("READY".equals(status)) {
                log.info("Demo data already loaded, skipping");
                return;
            }
        } catch (Exception e) {
            log.info("No existing demo data, loading...");
        }

        try {
            log.info("Loading demo seed data...");
            long start = System.currentTimeMillis();

            jdbcTemplate.update("DELETE FROM health_records WHERE upload_id = ?", DEMO_UPLOAD_ID);
            jdbcTemplate.update("DELETE FROM health_metric_daily WHERE upload_id = ?", DEMO_UPLOAD_ID);
            jdbcTemplate.update("DELETE FROM health_metric_weekly WHERE upload_id = ?", DEMO_UPLOAD_ID);
            jdbcTemplate.update("DELETE FROM health_metric_monthly WHERE upload_id = ?", DEMO_UPLOAD_ID);
            jdbcTemplate.update("DELETE FROM uploads WHERE id = ?", DEMO_UPLOAD_ID);

            Instant now = Instant.now();
            jdbcTemplate.update(
                    "INSERT INTO uploads (id, user_id, file_name, storage_key, file_size, status, record_count, source_count, " +
                    "coverage_start_at, coverage_end_at, created_at, started_at, finished_at) " +
                    "VALUES (?,?,?,?,0,'LOADING',0,0,?,?,?,?,?)",
                    DEMO_UPLOAD_ID, DEMO_USER_ID, "demo-export.zip", DEMO_STORAGE_KEY,
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));

            try (Connection conn = dataSource.getConnection()) {
                BaseConnection baseConn = conn.unwrap(BaseConnection.class);
                CopyManager cm = new CopyManager(baseConn);

                long recs = copyIn(cm, "samples/demo-records.csv",
                        "COPY health_records (upload_id, user_id, record_type, metric_key, category_key, " +
                        "source_name, value_numeric, value_text, unit, start_at, end_at, raw_payload_json) " +
                        "FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
                log.info("  records: {}", recs);

                long daily = copyIn(cm, "samples/demo-daily.csv",
                        "COPY health_metric_daily (upload_id, user_id, metric_key, date, value_avg, value_min, " +
                        "value_max, value_sum, sample_count, baseline_avg_30d, trend_delta_7d, trend_delta_30d, anomaly_flag) " +
                        "FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
                log.info("  daily:   {}", daily);

                long weekly = copyIn(cm, "samples/demo-weekly.csv",
                        "COPY health_metric_weekly (upload_id, user_id, metric_key, week_start, value_avg, value_min, " +
                        "value_max, value_sum, sample_count, baseline_avg_30d, trend_delta_7d, trend_delta_30d) " +
                        "FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
                log.info("  weekly:  {}", weekly);

                long monthly = copyIn(cm, "samples/demo-monthly.csv",
                        "COPY health_metric_monthly (upload_id, user_id, metric_key, month_start, value_avg, value_min, " +
                        "value_max, value_sum, sample_count, baseline_avg_30d, trend_delta_30d) " +
                        "FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
                log.info("  monthly: {}", monthly);
            }

            // Convert sleep_analysis categorical records to numeric sleep_duration
            int sleepFixed = jdbcTemplate.update(
                    "UPDATE health_records SET metric_key = 'sleep_duration', " +
                    "value_numeric = extract(epoch from (end_at - start_at)) / 3600.0, " +
                    "unit = 'h' " +
                    "WHERE upload_id = ? AND metric_key = 'sleep_analysis'", DEMO_UPLOAD_ID);
            log.info("  sleep_duration fixed: {} records", sleepFixed);

            // Rebuild daily sleep_duration metrics (demo CSVs don't include them)
            jdbcTemplate.update("DELETE FROM health_metric_daily WHERE upload_id = ? AND metric_key = 'sleep_duration'", DEMO_UPLOAD_ID);
            int dailySleep = jdbcTemplate.update(
                    "INSERT INTO health_metric_daily (upload_id, user_id, metric_key, date, " +
                    "value_avg, value_min, value_max, value_sum, sample_count) " +
                    "SELECT upload_id, user_id, 'sleep_duration', start_at::date, " +
                    "avg(value_numeric), min(value_numeric), max(value_numeric), sum(value_numeric), count(*) " +
                    "FROM health_records WHERE upload_id = ? AND metric_key = 'sleep_duration' AND value_numeric IS NOT NULL " +
                    "GROUP BY upload_id, user_id, start_at::date", DEMO_UPLOAD_ID);
            log.info("  sleep_duration daily rows: {}", dailySleep);

            jdbcTemplate.update(
                    "UPDATE uploads SET status = 'READY', " +
                    "record_count = (SELECT count(*) FROM health_records WHERE upload_id = ?), " +
                    "source_count = (SELECT count(DISTINCT source_name) FROM health_records WHERE upload_id = ? AND source_name IS NOT NULL), " +
                    "coverage_start_at = (SELECT min(start_at) FROM health_records WHERE upload_id = ?), " +
                    "coverage_end_at = (SELECT max(end_at) FROM health_records WHERE upload_id = ?), " +
                    "finished_at = ?, last_error = null WHERE id = ?",
                    DEMO_UPLOAD_ID, DEMO_UPLOAD_ID, DEMO_UPLOAD_ID, DEMO_UPLOAD_ID,
                    Timestamp.from(Instant.now()), DEMO_UPLOAD_ID);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Demo data loaded in {}ms", elapsed);
        } catch (Exception e) {
            log.error("Failed to load demo seed data", e);
            try {
                jdbcTemplate.update("UPDATE uploads SET status = 'FAILED', last_error = ? WHERE id = ?",
                        "Seed data load failed: " + e.getMessage(), DEMO_UPLOAD_ID);
            } catch (Exception ignored) {}
        }
    }

    private long copyIn(CopyManager cm, String resourcePath, String copySql) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return cm.copyIn(copySql, reader);
        }
    }
}
