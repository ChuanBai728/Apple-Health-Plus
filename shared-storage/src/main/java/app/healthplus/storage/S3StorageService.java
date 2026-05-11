package app.healthplus.storage;

import java.io.InputStream;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 兼容对象存储服务实现。
 * <p>将文件存储在 S3 兼容的对象存储中（如 AWS S3、MinIO），适用于生产环境和分布式部署。
 * 通过 {@code app.storage.type=s3} 配置激活。</p>
 *
 * <p>支持的配置属性：</p>
 * <ul>
 *   <li>{@code app.storage.s3.endpoint} — S3 服务端点地址</li>
 *   <li>{@code app.storage.s3.region} — AWS 区域标识</li>
 *   <li>{@code app.storage.s3.access-key} — 访问密钥 ID</li>
 *   <li>{@code app.storage.s3.secret-key} — 秘密访问密钥</li>
 *   <li>{@code app.storage.s3.bucket} — 存储桶名称</li>
 * </ul>
 *
 * @see StorageService
 * @see LocalStorageService
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    /** S3 客户端，用于执行对象操作（PUT/GET/DELETE/HEAD） */
    private final S3Client s3Client;

    /** S3 预签名器，用于生成带有时效的下载 URL */
    private final S3Presigner presigner;

    /** 存储桶名称 */
    private final String bucket;

    public S3StorageService(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.bucket}") String bucket
    ) {
        this.bucket = bucket;
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        this.s3Client = S3Client.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(java.net.URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        ensureBucket();
    }

    /**
     * 确保存储桶存在。
     * <p>启动时检查目标存储桶是否存在，不存在则自动创建。</p>
     */
    private void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    @Override
    public void store(String key, InputStream data, long contentLength, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromInputStream(data, contentLength)
        );
    }

    @Override
    public InputStream load(String key) {
        return s3Client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
        );
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        );
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public String presignUrl(String key, Duration ttl) {
        return presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build()
        ).url().toString();
    }
}
