package app.healthplus.domain;

public enum UploadStatus {
    CREATED,
    UPLOADING,
    UPLOADED,
    QUEUED,
    PARSING,
    PARSED,
    AGGREGATING,
    READY,
    FAILED,
    DELETED
}
