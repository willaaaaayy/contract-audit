package com.contractaudit.document;

import com.contractaudit.document.blob.BlobStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Управление метаданными документов. Изоляция по арендатору — автоматически через
 * {@code @TenantId} на сущности {@link Document}, поэтому здесь {@code tenant_id} нигде
 * не упоминается.
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final BlobStorage blobStorage;

    public DocumentService(DocumentRepository documentRepository, BlobStorage blobStorage) {
        this.documentRepository = documentRepository;
        this.blobStorage = blobStorage;
    }

    /**
     * Регистрирует загруженный документ ({@code PENDING}) и сохраняет его байты в одной
     * транзакции. После коммита документ долговечен: даже при рестарте поллер заберёт его на
     * обработку. Перевод в {@code PROCESSING} делает claim в пайплайне, не здесь.
     */
    @Transactional
    public Document register(String filename, UUID uploadedBy, byte[] content) {
        // saveAndFlush: документ должен попасть в БД ДО нативной вставки blob (там FK на documents).
        Document document = documentRepository.saveAndFlush(new Document(filename, uploadedBy));
        blobStorage.store(document.getId(), content);
        return document;
    }

    @Transactional
    public void markDone(UUID id) {
        documentRepository.findById(id).ifPresent(Document::markDone);
    }

    @Transactional
    public void markFailed(UUID id) {
        documentRepository.findById(id).ifPresent(Document::markFailed);
    }

    @Transactional(readOnly = true)
    public List<Document> list() {
        return documentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Document> get(UUID id) {
        return documentRepository.findById(id);
    }
}
