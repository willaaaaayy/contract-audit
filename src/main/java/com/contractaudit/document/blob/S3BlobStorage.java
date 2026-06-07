package com.contractaudit.document.blob;

import com.contractaudit.tenant.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранение blob в S3/MinIO. Ключ объекта — {@code <tenant_id>/<document_id>}: префикс по
 * арендатору и изолирует, и упорядочивает данные. Tenant берётся из {@link TenantContext}.
 */
@Component
@ConditionalOnProperty(prefix = "storage.blob", name = "type", havingValue = "s3")
public class S3BlobStorage implements BlobStorage {

    private final S3Client s3Client;
    private final String bucket;

    public S3BlobStorage(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
    }

    @Override
    public void store(UUID documentId, byte[] content) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key(documentId)).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> load(UUID documentId) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key(documentId)).build());
            return Optional.of(object.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    private static String key(UUID documentId) {
        return TenantContext.require() + "/" + documentId;
    }
}
