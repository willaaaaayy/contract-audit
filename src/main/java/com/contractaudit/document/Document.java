package com.contractaudit.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Метаданные загруженного контракта. Текст и эмбеддинги хранятся отдельно в
 * {@code document_chunks} (см. {@link com.contractaudit.chunk.DocumentChunkRepository}).
 *
 * <p>{@code @TenantId} обеспечивает автоматическую изоляцию по арендатору.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Document() {
    }

    public Document(String filename, UUID uploadedBy) {
        this.filename = filename;
        this.uploadedBy = uploadedBy;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markDone() {
        this.status = DocumentStatus.DONE;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = DocumentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFilename() {
        return filename;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
