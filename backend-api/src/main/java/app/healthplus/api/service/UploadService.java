package app.healthplus.api.service;

import app.healthplus.api.config.QueueNames;
import app.healthplus.api.repository.UploadRepository;
import app.healthplus.domain.Upload;
import app.healthplus.domain.dto.CreateUploadRequest;
import app.healthplus.domain.dto.CreateUploadResponse;
import app.healthplus.domain.dto.UploadStatusResponse;
import app.healthplus.messaging.ParseJobMessage;
import app.healthplus.storage.StorageService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务 —— 处理 Apple Health 导出 ZIP 文件的完整上传生命周期。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>接收用户上传的 ZIP 文件，存储到对象存储（MinIO / 本地文件系统）</li>
 *   <li>在数据库中创建上传记录（Upload 实体），跟踪上传状态</li>
 *   <li>上传完成后将解析任务投递到 RabbitMQ，触发 parse-worker 异步处理</li>
 *   <li>提供上传记录的查询和删除功能</li>
 * </ul>
 *
 * <h3>设计思路</h3>
 * <p>采用「先存储文件、再写库、最后发消息」的三步流程，每一步都通过 {@code @Transactional}
 * 保证数据库操作的原子性。文件存储使用抽象的 {@link StorageService}，可无缝切换
 * LocalStorageService（开发环境）和 S3StorageService（生产环境）。</p>
 *
 * @see StorageService
 * @see ParseJobMessage
 */
@Service
public class UploadService {

    private final UploadRepository uploadRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StorageService storageService;

    /**
     * 构造注入所有依赖。
     *
     * @param uploadRepository 上传记录的数据库仓储
     * @param rabbitTemplate   RabbitMQ 消息模板，用于投递解析任务
     * @param storageService   文件存储抽象层（Local / S3）
     */
    public UploadService(
            UploadRepository uploadRepository,
            RabbitTemplate rabbitTemplate,
            StorageService storageService
    ) {
        this.uploadRepository = uploadRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.storageService = storageService;
    }

    /**
     * 仅创建上传记录（不接收实际文件）。
     * <p>适用于前端直传场景：前端先获取 storageKey，再直接向对象存储上传文件。</p>
     *
     * @param request 包含文件名和文件大小的请求 DTO
     * @return 包含上传 ID、存储路径和状态的响应
     */
    @Transactional
    public CreateUploadResponse create(CreateUploadRequest request) {
        // 使用 UUID 构建唯一的存储路径，避免文件名冲突
        String storageKey = "uploads/" + UUID.randomUUID() + "/" + request.fileName();
        Upload upload = Upload.create(request.fileName(), storageKey, request.fileSize());
        uploadRepository.save(upload);
        return new CreateUploadResponse(
                upload.getId(),
                storageKey,
                storageKey,
                upload.getStatus().name().toLowerCase()
        );
    }

    /**
     * 接收并存储完整的 ZIP 文件，同时创建上传记录。
     * <p>适用于服务端直接受理上传的场景（如表单提交）。</p>
     *
     * @param file 用户上传的 ZIP 文件（MultipartFile）
     * @return 包含上传 ID、存储路径和状态的响应
     * @throws IllegalArgumentException 如果文件为空或非 .zip 格式
     * @throws IllegalStateException    如果文件存储失败
     */
    @Transactional
    public CreateUploadResponse create(MultipartFile file) {
        // 先校验文件格式，避免无效文件占用存储资源
        validateZip(file);

        // 提取原始文件名；Path.of().getFileName() 可剥离路径中的目录部分，防止路径注入
        String originalFileName = file.getOriginalFilename() == null
                ? "export.zip"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        String storageKey = "uploads/" + UUID.randomUUID() + "/" + originalFileName;

        // 将文件流写入对象存储
        try {
            storageService.store(storageKey, file.getInputStream(), file.getSize(), "application/zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded ZIP", e);
        }

        // 文件存储成功后才写库，保证存储与数据库状态一致
        Upload upload = Upload.create(originalFileName, storageKey, file.getSize());
        upload.markUploaded();
        uploadRepository.save(upload);
        return new CreateUploadResponse(
                upload.getId(),
                storageKey,
                storageKey,
                upload.getStatus().name().toLowerCase()
        );
    }

    /**
     * 完成上传流程：校验文件存在性后，将解析任务投递到 RabbitMQ。
     * <p>此方法是前端「确认上传完成」的回调端点。即使文件已通过直传上传到存储，
     * 也需要调用此方法来触发后续的 XML 解析流程。</p>
     *
     * @param uploadId 上传记录的 UUID
     * @return 最新的上传状态响应
     * @throws IllegalArgumentException 如果上传记录不存在
     */
    @Transactional
    public UploadStatusResponse complete(UUID uploadId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        // 防御性检查：确保文件确实存在于存储中，避免下游 worker 拉取失败
        if (!storageService.exists(upload.getStorageKey())) {
            upload.markFailed("Uploaded ZIP not found: " + upload.getStorageKey());
            return new UploadStatusResponse(
                    upload.getId(),
                    upload.getStatus().name().toLowerCase(),
                    upload.getFileName(),
                    upload.getCreatedAt(),
                    upload.getLastError()
            );
        }

        upload.markUploaded();

        // 生成唯一消息 ID，用于后续追踪消息投递和消费状态
        String messageId = UUID.randomUUID().toString();
        upload.markQueued(messageId);

        // 将解析任务发送到 PARSE_QUEUE，parse-worker 会消费此消息
        rabbitTemplate.convertAndSend(
                QueueNames.PARSE_QUEUE,
                new ParseJobMessage(upload.getId(), upload.getUserId(), upload.getStorageKey(), messageId, Instant.now())
        );
        return new UploadStatusResponse(
                upload.getId(),
                upload.getStatus().name().toLowerCase(),
                upload.getFileName(),
                upload.getCreatedAt(),
                upload.getLastError()
        );
    }

    /**
     * 校验上传文件的基本合法性。
     *
     * @param file 待校验的文件
     * @throws IllegalArgumentException 如果文件为空或扩展名不是 .zip
     */
    private void validateZip(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported");
        }
    }

    /**
     * 删除上传记录及其对应的存储文件。
     * <p>文件删除采用「尽力而为」策略 —— 即使文件不存在也不会抛异常，
     * 因为文件可能已被清理或从未成功上传。</p>
     *
     * @param uploadId 上传记录的 UUID
     * @throws IllegalArgumentException 如果上传记录不存在
     */
    @Transactional
    public void delete(UUID uploadId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        // 文件删除是幂等的，失败不影响数据库记录的清理
        try {
            storageService.delete(upload.getStorageKey());
        } catch (Exception ignored) {
            // File may already be gone; deletion is best-effort
        }

        uploadRepository.delete(upload);
    }

    /**
     * 查询单个上传记录的状态信息（只读事务）。
     *
     * @param uploadId 上传记录的 UUID
     * @return 上传状态响应，包含状态、文件名、创建时间和错误信息
     * @throws IllegalArgumentException 如果上传记录不存在
     */
    @Transactional(readOnly = true)
    public UploadStatusResponse get(UUID uploadId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));
        return new UploadStatusResponse(
                upload.getId(),
                upload.getStatus().name().toLowerCase(),
                upload.getFileName(),
                upload.getCreatedAt(),
                upload.getLastError()
        );
    }
}
