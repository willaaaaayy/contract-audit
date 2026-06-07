package com.contractaudit.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Метаданные документов. Tenant-фильтрация — автоматически через {@code @TenantId},
 * поэтому {@code findAll}/{@code findById} никогда не вернут чужой документ.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByStatus(DocumentStatus status);
}
