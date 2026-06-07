package com.contractaudit.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Риски документов. Tenant-фильтрация — автоматически через {@code @TenantId}. */
public interface DocumentRiskRepository extends JpaRepository<DocumentRisk, UUID> {

    List<DocumentRisk> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
