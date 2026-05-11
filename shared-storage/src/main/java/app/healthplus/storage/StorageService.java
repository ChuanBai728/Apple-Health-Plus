package app.healthplus.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储服务统一接口。
 * <p>定义了文件存储操作的标准契约，支持本地文件系统（{@link LocalStorageService}）
 * 和 S3 兼容存储（{@link S3StorageService}）两种实现，通过 Spring 条件注解自动切换。</p>
 */
public interface StorageService {

    /**
     * 存储文件。
     *
     * @param key           文件唯一键（路径标识）
     * @param data          文件输入流
     * @param contentLength 文件大小（字节）
     * @param contentType   MIME 类型（如 "application/zip"）
     */
    void store(String key, InputStream data, long contentLength, String contentType);

    /**
     * 加载文件。
     *
     * @param key 文件唯一键
     * @return 文件输入流
     */
    InputStream load(String key);

    /**
     * 删除文件。
     *
     * @param key 文件唯一键
     */
    void delete(String key);

    /**
     * 检查文件是否存在。
     *
     * @param key 文件唯一键
     * @return 存在返回 true
     */
    boolean exists(String key);

    /**
     * 生成预签名下载 URL。
     * <p>用于生成有时效性的文件下载链接，客户端可直接通过此 URL 下载文件。</p>
     *
     * @param key 文件唯一键
     * @param ttl URL 有效期
     * @return 预签名 URL 字符串
     */
    String presignUrl(String key, Duration ttl);
}
