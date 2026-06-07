package com.contractaudit.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Findings сверки. Tenant-фильтрация — автоматически через {@code @TenantId}. */
public interface ComplianceFindingRepository extends JpaRepository<ComplianceFinding, UUID> {

    List<ComplianceFinding> findByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
