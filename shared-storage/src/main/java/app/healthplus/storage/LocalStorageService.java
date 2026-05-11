package app.healthplus.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 本地文件系统存储服务实现。
 * <p>将文件存储在本地磁盘目录中，适用于开发环境和单机部署场景。
 * 通过 {@code app.storage.type=local} 配置激活（默认实现）。</p>
 *
 * <p>存储根目录通过 {@code app.storage.root} 配置，默认为 {@code ./local-storage}。</p>
 *
 * @see StorageService
 * @see S3StorageService
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    /** 本地存储根目录的绝对路径 */
    private final Path storageRoot;

    public LocalStorageService(@Value("${app.storage.root:./local-storage}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream data, long contentLength, String contentType) {
        try {
            Path target = storageRoot.resolve(key).normalize();
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file: " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            Path target = storageRoot.resolve(key).normalize();
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path target = storageRoot.resolve(key).normalize();
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete file: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        Path target = storageRoot.resolve(key).normalize();
        return Files.exists(target);
    }

    @Override
    public String presignUrl(String key, Duration ttl) {
        return storageRoot.resolve(key).normalize().toUri().toString();
    }
}
