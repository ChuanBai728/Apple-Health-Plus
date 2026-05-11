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

/**
 * 演示数据初始化器。
 * <p>
 * 应用启动时自动加载预置的健康数据样本，用于演示和开发调试。
 * 使用 PostgreSQL 的 COPY 命令批量导入 CSV 数据，性能远优于逐行 INSERT。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>检查 uploads 表中是否已有 READY 状态的演示数据，有则跳过</li>
 *   <li>清理旧的演示数据（按外键依赖顺序：records -> daily/weekly/monthly -> uploads）</li>
 *   <li>插入 LOADING 状态的 upload 记录</li>
 *   <li>通过 COPY 命令批量导入 4 张表的 CSV 数据</li>
 *   <li>修复 sleep_analysis 记录：将分类记录转换为数值型的 sleep_duration（小时）</li>
 *   <li>重建 sleep_duration 的日聚合指标</li>
 *   <li>更新 upload 记录为 READY 状态，填充记录数、数据源数、覆盖时间范围</li>
 * </ol>
 * <p>
 * 失败时将 upload 状态标记为 FAILED 并记录错误信息，不影响应用正常启动。
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    /** 演示用户 ID，用于隔离演示数据与真实用户数据 */
    private static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** 演示上传记录 ID，固定值便于幂等判断和数据清理 */
    private static final UUID DEMO_UPLOAD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    /** 演示文件在 MinIO/LocalStorage 中的存储路径 */
    private static final String DEMO_STORAGE_KEY = "demo/demo-export.zip";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动后执行的入口方法。
     * 先检查演示数据是否已加载（幂等性），然后按步骤执行数据导入。
     */
    @Override
    public void run(ApplicationArguments args) {
        // 幂等检查：若演示数据已处于 READY 状态则直接跳过，避免重复加载
        try {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM uploads WHERE id = ?",
                    String.class, DEMO_UPLOAD_ID);
            if ("READY".equals(status)) {
                log.info("Demo data already loaded, skipping");
                return;
            }
        } catch (Exception e) {
            // 查询异常说明记录不存在，首次加载场景
            log.info("No existing demo data, loading...");
        }

        try {
            log.info("Loading demo seed data...");
            long start = System.currentTimeMillis();

            // 按外键依赖顺序清理旧数据，避免外键约束冲突
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

            // 使用 PostgreSQL JDBC CopyManager 批量导入 CSV，性能远高于逐行 INSERT
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

            // 修复 sleep_analysis：原始数据中睡眠记录是分类类型（start/end 时间对），
            // 转换为数值型 sleep_duration（单位：小时），便于后续聚合计算
            int sleepFixed = jdbcTemplate.update(
                    "UPDATE health_records SET metric_key = 'sleep_duration', " +
                    "value_numeric = extract(epoch from (end_at - start_at)) / 3600.0, " +
                    "unit = 'h' " +
                    "WHERE upload_id = ? AND metric_key = 'sleep_analysis'", DEMO_UPLOAD_ID);
            log.info("  sleep_duration fixed: {} records", sleepFixed);

            // 重建 sleep_duration 的日聚合指标（演示 CSV 中不包含此指标的预聚合数据）
            jdbcTemplate.update("DELETE FROM health_metric_daily WHERE upload_id = ? AND metric_key = 'sleep_duration'", DEMO_UPLOAD_ID);
            int dailySleep = jdbcTemplate.update(
                    "INSERT INTO health_metric_daily (upload_id, user_id, metric_key, date, " +
                    "value_avg, value_min, value_max, value_sum, sample_count) " +
                    "SELECT upload_id, user_id, 'sleep_duration', start_at::date, " +
                    "avg(value_numeric), min(value_numeric), max(value_numeric), sum(value_numeric), count(*) " +
                    "FROM health_records WHERE upload_id = ? AND metric_key = 'sleep_duration' AND value_numeric IS NOT NULL " +
                    "GROUP BY upload_id, user_id, start_at::date", DEMO_UPLOAD_ID);
            log.info("  sleep_duration daily rows: {}", dailySleep);

            // 所有数据导入完成后，更新 upload 记录为 READY，填充统计信息
            jdbcTemplate.update(
                    "UPDATE uploads SET status = 'READY'," +
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
            // 标记为 FAILED 状态，便于前端展示错误；外层 catch 异常则忽略（尽力记录）
            try {
                jdbcTemplate.update("UPDATE uploads SET status = 'FAILED', last_error = ? WHERE id = ?",
                        "Seed data load failed: " + e.getMessage(), DEMO_UPLOAD_ID);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 从 classpath 资源文件读取 CSV 数据并通过 COPY 命令批量导入 PostgreSQL。
     *
     * @param cm           PostgreSQL CopyManager 实例
     * @param resourcePath classpath 下的 CSV 文件路径
     * @param copySql      COPY 命令 SQL（含表名、列名、格式选项）
     * @return 导入的行数
     * @throws Exception 读取文件或执行 COPY 失败时抛出
     */
    private long copyIn(CopyManager cm, String resourcePath, String copySql) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return cm.copyIn(copySql, reader);
        }
    }
}
