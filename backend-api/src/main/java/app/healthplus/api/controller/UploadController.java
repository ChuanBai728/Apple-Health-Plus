package app.healthplus.api.controller;

import app.healthplus.api.service.UploadService;
import app.healthplus.domain.dto.CreateUploadRequest;
import app.healthplus.domain.dto.CreateUploadResponse;
import app.healthplus.domain.dto.UploadStatusResponse;
import jakarta.validation.Valid;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    public CreateUploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
        return uploadService.create(request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CreateUploadResponse uploadFile(@RequestPart("file") MultipartFile file) {
        return uploadService.create(file);
    }

    @PostMapping("/{id}/complete")
    public UploadStatusResponse complete(@PathVariable("id") UUID id) {
        return uploadService.complete(id);
    }

    @GetMapping("/{id}")
    public UploadStatusResponse get(@PathVariable("id") UUID id) {
        return uploadService.get(id);
    }

    @DeleteMapping("/{id}")
    public java.util.Map<String, String> delete(@PathVariable("id") UUID id) {
        uploadService.delete(id);
        return java.util.Map.of("status", "deleted", "uploadId", id.toString());
    }
}
