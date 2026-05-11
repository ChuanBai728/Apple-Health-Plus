package app.healthplus.domain.dto;

import java.util.UUID;

/**
 * 创建上传任务响应 DTO。
 * <p>用户发起文件上传后返回，包含上传凭证和状态信息。</p>
 *
 * @param uploadId   上传记录唯一标识，后续用于查询处理状态
 * @param storageKey 文件在存储系统中的唯一键
 * @param uploadUrl  预签名上传地址（客户端可直接 PUT 文件到此 URL）
 * @param status     初始状态（通常为 "PENDING"）
 */
public record CreateUploadResponse(
        UUID uploadId,
        String storageKey,
        String uploadUrl,
        String status
) {
}
