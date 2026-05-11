package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 文件上传记录实体，对应数据库表 {@code uploads}。
 * <p>
 * 记录用户上传的 Apple Health 导出文件（export.zip）的完整生命周期，
 * 包括上传、消息队列投递、解析、聚合等各阶段的状态与耗时信息。
 * </p>
 *
 * <h3>关键字段说明</h3>
 * <ul>
 *   <li>{@code storageKey} — 文件在 MinIO/LocalStorage 中的存储路径</li>
 *   <li>{@code status} — 上传处理状态，参见 {@link UploadStatus}</li>
 *   <li>{@code messageId} — 投递到 RabbitMQ 后返回的消息 ID，用于追踪消费进度</li>
 *   <li>{@code recordCount / sourceCount} — 解析完成后的健康记录总数和数据来源数</li>
 *   <li>{@code coverageStartAt / coverageEndAt} — 健康数据覆盖的时间范围</li>
 * </ul>
 */
@Entity
@Table(name = "uploads")
public class Upload {

    /** JPA 要求的无参构造方法，仅供框架反射使用 */
    protected Upload() {
    }

    @Id
    private UUID id;

    /** 所属用户 ID，外键关联 users 表 */
    @Column(nullable = false)
    private UUID userId;

    /** 原始文件名（如 export.zip），最大 256 字符 */
    @Column(nullable = false, length = 256)
    private String fileName;

    /** 文件在对象存储中的 key/路径，最大 512 字符 */
    @Column(nullable = false, length = 512)
    private String storageKey;

    /** 文件大小（字节） */
    @Column(nullable = false)
    private long fileSize;

    /** 上传处理的当前状态，以字符串形式持久化到数据库 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadStatus status;

    /** RabbitMQ 消息 ID，用于将上传记录与队列消息关联，便于幂等处理和追踪 */
    @Column(length = 128)
    private String messageId;

    /** 失败重试次数，每次重试递增 */
    private int retryCount;

    /** 最近一次失败的错误信息，最大 1000 字符 */
    @Column(length = 1000)
    private String lastError;

    /** 下次重试的计划时间 */
    private Instant nextRetryAt;

    /** 解析/聚合任务开始执行的时间 */
    private Instant startedAt;

    /** 解析/聚合任务完成的时间 */
    private Instant finishedAt;

    /** 解析后的健康记录总条数 */
    private long recordCount;

    /** 解析后的数据来源（设备/App）数量 */
    private int sourceCount;

    /** 健康数据覆盖的最早时间点 */
    private Instant coverageStartAt;

    /** 健康数据覆盖的最晚时间点 */
    private Instant coverageEndAt;

    /** 记录创建时间 */
    private Instant createdAt;

    /**
     * 工厂方法：创建一个新的上传记录。
     * <p>
     * 自动生成 UUID 主键，将状态设为 {@link UploadStatus#CREATED}，
     * 记录创建时间，并关联到默认用户（硬编码占位）。
     * </p>
     *
     * @param fileName  原始文件名
     * @param storageKey 对象存储中的文件路径
     * @param fileSize  文件大小（字节）
     * @return 初始化完成的 Upload 实例（尚未持久化）
     */
    public static Upload create(String fileName, String storageKey, long fileSize) {
        Upload upload = new Upload();
        upload.id = UUID.randomUUID();
        upload.userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        upload.fileName = fileName;
        upload.storageKey = storageKey;
        upload.fileSize = fileSize;
        upload.status = UploadStatus.CREATED;
        upload.createdAt = Instant.now();
        return upload;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getFileSize() {
        return fileSize;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getRecordCount() {
        return recordCount;
    }

    public int getSourceCount() {
        return sourceCount;
    }

    public Instant getCoverageStartAt() {
        return coverageStartAt;
    }

    public Instant getCoverageEndAt() {
        return coverageEndAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 将上传状态标记为 {@link UploadStatus#UPLOADED}，
     * 表示文件已成功存储到 MinIO/本地存储。
     */
    public void markUploaded() {
        this.status = UploadStatus.UPLOADED;
    }

    /**
     * 将上传状态标记为 {@link UploadStatus#QUEUED}，
     * 并记录对应的 RabbitMQ 消息 ID，表示解析任务已投递到消息队列。
     *
     * @param messageId RabbitMQ 返回的消息 ID
     */
    public void markQueued(String messageId) {
        this.status = UploadStatus.QUEUED;
        this.messageId = messageId;
    }

    /**
     * 将上传状态标记为 {@link UploadStatus#FAILED}，
     * 记录错误信息并设置完成时间为当前时刻。
     *
     * @param errorMessage 失败原因的描述信息
     */
    public void markFailed(String errorMessage) {
        this.status = UploadStatus.FAILED;
        this.lastError = errorMessage;
        this.finishedAt = Instant.now();
    }
}
