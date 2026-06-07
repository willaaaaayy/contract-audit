package com.contractaudit.document.blob;

import com.contractaudit.document.BlobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранение blob в Postgres (таблица {@code document_blobs}). Реализация по умолчанию —
 * включается, если {@code storage.blob.type} не задан или равен {@code db}.
 */
@Component
@ConditionalOnProperty(prefix = "storage.blob", name = "type", havingValue = "db", matchIfMissing = true)
public class DbBlobStorage implements BlobStorage {

    private final BlobRepository blobRepository;

    public DbBlobStorage(BlobRepository blobRepository) {
        this.blobRepository = blobRepository;
    }

    @Override
    public void store(UUID documentId, byte[] content) {
        blobRepository.save(documentId, content);
    }

    @Override
    public Optional<byte[]> load(UUID documentId) {
        return blobRepository.load(documentId);
    }
}
