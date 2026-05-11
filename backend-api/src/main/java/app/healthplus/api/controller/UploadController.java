package app.healthplus.api.controller;

import app.healthplus.api.service.UploadService;
import app.healthplus.domain.dto.CreateUploadRequest;
import app.healthplus.domain.dto.CreateUploadResponse;
import app.healthplus.domain.dto.UploadStatusResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器。
 * <p>
 * 职责：管理 Apple Health 导出文件（export.zip）的上传生命周期，
 * 包括创建上传记录、接收文件流、标记上传完成、查询状态和删除。
 * </p>
 * <p>
 * 对应 API 端点：{@code /api/v1/uploads}
 * </p>
 * <p>
 * 上传流程：create → uploadFile → complete，完成后触发解析/聚合流水线。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    /** 上传服务 —— 处理上传记录的 CRUD 及文件存储 */
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /**
     * 创建上传记录（JSON 方式）。
     * <p>
     * 前端先提交上传元信息，后端返回预签名 URL 或存储 key，
     * 前端再将文件上传到指定位置。
     * </p>
     *
     * @param request 上传创建请求，包含文件名等元信息
     * @return 上传创建响应，包含 uploadId 和上传地址
     */
    @PostMapping
    public CreateUploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
        return uploadService.create(request);
    }

    /**
     * 直接上传文件（multipart 方式）。
     * <p>
     * 前端直接将文件以 multipart/form-data 形式提交，
     * 后端同时完成记录创建和文件存储。
     * </p>
     *
     * @param file 上传的 ZIP 文件
     * @return 上传创建响应，包含 uploadId 和存储 key
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreateUploadResponse uploadFile(@RequestPart("file") MultipartFile file) {
        return uploadService.create(file);
    }

    /**
     * 标记上传完成，触发后续处理流水线。
     * <p>
     * 完成操作会清除所有报告相关的缓存（overview / metrics / insight），
     * 因为新数据上传后，旧缓存已失效。
     * </p>
     *
     * @param id 上传记录 ID
     * @return 上传状态响应，包含当前处理进度
     */
    @PostMapping("/{id}/complete")
    @CacheEvict(value = {"overview", "metrics", "insight"}, allEntries = true)
    public UploadStatusResponse complete(@PathVariable("id") UUID id) {
        return uploadService.complete(id);
    }

    /**
     * 查询上传记录状态。
     *
     * @param id 上传记录 ID
     * @return 上传状态响应，包含文件名、处理状态、进度等
     */
    @GetMapping("/{id}")
    public UploadStatusResponse get(@PathVariable("id") UUID id) {
        return uploadService.get(id);
    }

    /**
     * 删除上传记录及其关联数据。
     *
     * @param id 上传记录 ID
     * @return 包含状态和 uploadId 的确认信息
     */
    @DeleteMapping("/{id}")
    public java.util.Map<String, String> delete(@PathVariable("id") UUID id) {
        uploadService.delete(id);
        return java.util.Map.of("status", "deleted", "uploadId", id.toString());
    }
}
