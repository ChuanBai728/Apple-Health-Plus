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

@Service
public class UploadService {

    private final UploadRepository uploadRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StorageService storageService;

    public UploadService(
            UploadRepository uploadRepository,
            RabbitTemplate rabbitTemplate,
            StorageService storageService
    ) {
        this.uploadRepository = uploadRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.storageService = storageService;
    }

    @Transactional
    public CreateUploadResponse create(CreateUploadRequest request) {
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

    @Transactional
    public CreateUploadResponse create(MultipartFile file) {
        validateZip(file);

        String originalFileName = file.getOriginalFilename() == null
                ? "export.zip"
                : Path.of(file.getOriginalFilename()).getFileName().toString();
        String storageKey = "uploads/" + UUID.randomUUID() + "/" + originalFileName;

        try {
            storageService.store(storageKey, file.getInputStream(), file.getSize(), "application/zip");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded ZIP", e);
        }

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

    @Transactional
    public UploadStatusResponse complete(UUID uploadId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

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
        String messageId = UUID.randomUUID().toString();
        upload.markQueued(messageId);
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

    private void validateZip(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported");
        }
    }

    @Transactional
    public void delete(UUID uploadId) {
        Upload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        try {
            storageService.delete(upload.getStorageKey());
        } catch (Exception ignored) {
            // File may already be gone; deletion is best-effort
        }

        uploadRepository.delete(upload);
    }

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
