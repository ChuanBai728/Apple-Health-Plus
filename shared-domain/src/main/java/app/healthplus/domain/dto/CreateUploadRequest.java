package app.healthplus.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建文件上传任务请求 DTO。
 * <p>客户端发起健康数据文件上传前，先提交文件元信息以获取上传凭证。</p>
 *
 * @param fileName 原始文件名（不可为空，如 "export.zip"）
 * @param fileSize 文件大小（字节，必须 >= 1）
 */
public record CreateUploadRequest(
        @NotBlank String fileName,
        @Min(1) long fileSize
) {
}
