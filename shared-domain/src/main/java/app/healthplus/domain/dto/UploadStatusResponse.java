package app.healthplus.domain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 文件上传状态查询响应 DTO。
 * <p>用于返回健康数据文件的上传处理状态，支持前端轮询进度。</p>
 *
 * @param uploadId  上传记录唯一标识
 * @param status    处理状态（如 PENDING、PARSING、AGGREGATING、COMPLETED、FAILED）
 * @param fileName  原始文件名
 * @param createdAt 上传创建时间
 * @param lastError 最近一次错误信息（仅失败时有值）
 */
public record UploadStatusResponse(
        UUID uploadId,
        String status,
        String fileName,
        Instant createdAt,
        String lastError
) {
}
