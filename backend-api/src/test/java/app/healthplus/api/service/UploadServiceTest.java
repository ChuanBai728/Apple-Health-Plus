package app.healthplus.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.healthplus.api.repository.UploadRepository;
import app.healthplus.domain.Upload;
import app.healthplus.domain.dto.CreateUploadRequest;
import app.healthplus.domain.dto.CreateUploadResponse;
import app.healthplus.storage.LocalStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;

class UploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPrepareLocalUploadPath() {
        UploadRepository repository = mock(UploadRepository.class);
        when(repository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        LocalStorageService storage = new LocalStorageService(tempDir.toString());
        UploadService service = new UploadService(repository, rabbitTemplate, storage);

        CreateUploadResponse response = service.create(new CreateUploadRequest("export.zip", 1024));

        assertEquals("created", response.status());
        assertTrue(Files.exists(tempDir.resolve(response.uploadUrl()).getParent()));
    }

    @Test
    void shouldStoreMultipartZipToLocalStorage() throws Exception {
        UploadRepository repository = mock(UploadRepository.class);
        when(repository.save(any(Upload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RabbitTemplate rabbitTemplate = new RabbitTemplate();
        LocalStorageService storage = new LocalStorageService(tempDir.toString());
        UploadService service = new UploadService(repository, rabbitTemplate, storage);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "export.zip",
                "application/zip",
                "zip-content".getBytes()
        );

        CreateUploadResponse response = service.create(file);

        assertEquals("uploaded", response.status());
        assertTrue(Files.exists(tempDir.resolve(response.uploadUrl())));
        assertEquals("zip-content", Files.readString(tempDir.resolve(response.uploadUrl())));
    }
}
