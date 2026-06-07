package com.contractaudit.document.processing;

import com.contractaudit.document.processing.ProcessingJobRepository.ClaimableJob;
import com.contractaudit.support.AbstractPgvectorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Атомарный claim и восстановление зависших документов — основа долговечности пайплайна.
 */
class ProcessingJobRepositoryTest extends AbstractPgvectorTest {

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Test
    void claim_isAtomic_andReclaimsStaleProcessing() {
        UUID tenant = createTenant("acme");
        UUID documentId = createDocument(tenant, "contract.pdf");   // создаётся PENDING

        Instant past = Instant.now().minusSeconds(3600);
        Instant future = Instant.now().plusSeconds(3600);

        // PENDING → claim удаётся, документ становится PROCESSING.
        assertThat(jobRepository.tryClaim(documentId, past)).isTrue();

        // Уже PROCESSING и не зависший (порог в прошлом) → повторный claim не проходит.
        assertThat(jobRepository.tryClaim(documentId, past)).isFalse();

        // Порог в будущем → PROCESSING считается зависшим → переклеймливается (после краша).
        assertThat(jobRepository.tryClaim(documentId, future)).isTrue();
    }

    @Test
    void findClaimable_seesPendingAcrossTenants_withTenantAttached() {
        UUID tenantA = createTenant("tenant-A");
        UUID docA = createDocument(tenantA, "a.pdf");
        UUID tenantB = createTenant("tenant-B");
        UUID docB = createDocument(tenantB, "b.pdf");

        // Системный запрос (мимо @TenantId) видит документы разных арендаторов.
        List<ClaimableJob> jobs = jobRepository.findClaimable(Instant.now(), 100_000);

        assertThat(jobs).contains(new ClaimableJob(docA, tenantA), new ClaimableJob(docB, tenantB));
    }
}
