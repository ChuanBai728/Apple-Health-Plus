package app.healthplus.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    void store(String key, InputStream data, long contentLength, String contentType);

    InputStream load(String key);

    void delete(String key);

    boolean exists(String key);

    String presignUrl(String key, Duration ttl);
}
