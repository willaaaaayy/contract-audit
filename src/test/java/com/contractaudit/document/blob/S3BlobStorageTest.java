package com.contractaudit.document.blob;

import com.contractaudit.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * S3-хранилище blob против MinIO (docker compose). Если MinIO недоступен — тест пропускается.
 * Проверяет roundtrip store/load и изоляцию по ключу-префиксу арендатора.
 */
class S3BlobStorageTest {

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String BUCKET = "contracts-test";
    private static S3Client s3;
    private static boolean available;

    @BeforeAll
    static void setUp() {
        try {
            s3 = S3Client.builder()
                    .endpointOverride(URI.create(ENDPOINT))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("minioadmin", "minioadmin")))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            } catch (RuntimeException alreadyExists) {
                // бакет уже есть — это нормально
            }
            available = true;
        } catch (RuntimeException notReachable) {
            available = false;
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void storesAndLoadsRoundTrip() {
        assumeTrue(available, "MinIO недоступен на " + ENDPOINT + " — пропускаю");
        S3BlobStorage storage = storage();
        TenantContext.set(UUID.randomUUID());

        UUID documentId = UUID.randomUUID();
        byte[] content = "contract pdf bytes".getBytes(StandardCharsets.UTF_8);
        storage.store(documentId, content);

        Optional<byte[]> loaded = storage.load(documentId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(content);
    }

    @Test
    void isolatesByTenantPrefix() {
        assumeTrue(available, "MinIO недоступен — пропускаю");
        S3BlobStorage storage = storage();

        UUID tenantA = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        TenantContext.set(tenantA);
        storage.store(documentId, "A".getBytes(StandardCharsets.UTF_8));

        // Другой арендатор по тому же documentId ничего не видит (ключ <tenant>/<doc> другой).
        TenantContext.set(UUID.randomUUID());
        assertThat(storage.load(documentId)).isEmpty();
    }

    private static S3BlobStorage storage() {
        return new S3BlobStorage(s3, new S3Properties(ENDPOINT, "us-east-1", BUCKET,
                "minioadmin", "minioadmin", true));
    }
}
