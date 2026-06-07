package com.contractaudit.document.blob;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранилище сырых байтов документов. Реализации: {@link DbBlobStorage} (Postgres, по умолчанию)
 * и {@link S3BlobStorage} (S3/MinIO). Tenant-scoped: ключ/строка привязаны к текущему арендатору.
 */
public interface BlobStorage {

    void store(UUID documentId, byte[] content);

    Optional<byte[]> load(UUID documentId);
}
