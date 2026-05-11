package app.healthplus.domain;

/**
 * 上传文件处理状态枚举，定义了文件从上传到就绪的完整生命周期。
 *
 * <pre>
 * 状态流转路径：
 *   CREATED → UPLOADING → UPLOADED → QUEUED → PARSING → PARSED → AGGREGATING → READY
 *                                                                 ↑
 *                                           任意阶段失败 ──→ FAILED（可重试）
 *
 *   终态：READY（正常使用）/ FAILED（最终失败）/ DELETED（用户删除）
 * </pre>
 */
public enum UploadStatus {
    /** 已创建：上传记录已生成，文件尚未开始传输 */
    CREATED,
    /** 上传中：文件正在传输到存储服务 */
    UPLOADING,
    /** 已上传：文件已成功存储到 MinIO/本地，等待投递解析任务 */
    UPLOADED,
    /** 已排队：解析任务已投递到 RabbitMQ，等待消费者处理 */
    QUEUED,
    /** 解析中：parse-worker 正在解析 XML 健康数据 */
    PARSING,
    /** 已解析：XML 解析完成，数据已写入数据库 */
    PARSED,
    /** 聚合中：aggregation-worker 正在计算日/周/月聚合指标 */
    AGGREGATING,
    /** 就绪：所有处理完成，用户可以查看报告和进行 AI 对话 */
    READY,
    /** 失败：处理过程中出现错误，可通过重试机制恢复 */
    FAILED,
    /** 已删除：用户主动删除了该上传记录 */
    DELETED
}
