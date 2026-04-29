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

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
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
