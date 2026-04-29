package app.healthplus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploads")
public class Upload {

    protected Upload() {
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 512)
    private String storageKey;

    @Column(nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadStatus status;

    @Column(length = 128)
    private String messageId;

    private int retryCount;

    @Column(length = 1000)
    private String lastError;

    private Instant nextRetryAt;
    private Instant startedAt;
    private Instant finishedAt;
    private long recordCount;
    private int sourceCount;
    private Instant coverageStartAt;
    private Instant coverageEndAt;
    private Instant createdAt;

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

    public void markUploaded() {
        this.status = UploadStatus.UPLOADED;
    }

    public void markQueued(String messageId) {
        this.status = UploadStatus.QUEUED;
        this.messageId = messageId;
    }

    public void markFailed(String errorMessage) {
        this.status = UploadStatus.FAILED;
        this.lastError = errorMessage;
        this.finishedAt = Instant.now();
    }
}
