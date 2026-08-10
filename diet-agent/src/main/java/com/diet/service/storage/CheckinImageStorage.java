package com.diet.service.storage;

import com.diet.exception.DietException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.UUID;

/** Private MinIO-backed storage for check-in images. Object keys never come from clients. */
@Service
public class CheckinImageStorage {
    private final MinioClient minioClient;
    private final String bucket;

    public CheckinImageStorage(
            @Value("${diet.storage.minio.endpoint}") String endpoint,
            @Value("${diet.storage.minio.access-key}") String accessKey,
            @Value("${diet.storage.minio.secret-key}") String secretKey,
            @Value("${diet.storage.minio.bucket}") String bucket
    ) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法连接 MinIO 或初始化餐食图片 Bucket", exception);
        }
    }

    public String storeForCheckin(Long userId, String referenceId, byte[] data, String mediaType) {
        String objectKey = "checkins/" + userId + "/" + referenceId + extensionFor(mediaType);
        store(objectKey, data, mediaType);
        return objectKey;
    }

    public void store(String objectKey, byte[] data, String mediaType) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, data.length, -1)
                    .contentType(mediaType)
                    .build());
        } catch (Exception exception) {
            throw new DietException("上传餐食图片到对象存储失败", exception);
        }
    }

    public StoredImage load(String objectKey, String mediaType) {
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return new StoredImage(response.readAllBytes(), mediaType);
        } catch (Exception exception) {
            throw new DietException("读取餐食图片失败", exception);
        }
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw new DietException("删除餐食图片失败", exception);
        }
    }

    public String legacyObjectKey(String category, Long userId, String identifier, String mediaType) {
        return "legacy/" + category + "/" + userId + "/" + identifier + extensionFor(mediaType);
    }

    private String extensionFor(String mediaType) {
        return switch ((mediaType == null ? "" : mediaType).toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public record StoredImage(byte[] data, String mediaType) {
    }
}
