package com.contractaudit.document.processing;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Системный доступ к очереди обработки. В отличие от прочих репозиториев — НЕ tenant-scoped:
 * фоновый поллер обслуживает все арендаторы сразу, поэтому запросы идут мимо {@code @TenantId}
 * (нативный SQL по таблице documents). {@code tenant_id} возвращается с каждой задачей, чтобы
 * обработка дальше шла в контексте нужного арендатора.
 */
@Repository
public class ProcessingJobRepository {

    private final JdbcClient jdbcClient;

    public ProcessingJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Документ, готовый к обработке: id + его арендатор. */
    public record ClaimableJob(UUID documentId, UUID tenantId) {
    }

    /**
     * Документы, которые можно взять в работу: новые ({@code PENDING}) и зависшие
     * ({@code PROCESSING} дольше таймаута — например, после краша приложения).
     */
    @Transactional(readOnly = true)
    public List<ClaimableJob> findClaimable(Instant staleBefore, int limit) {
        return jdbcClient.sql("""
                        SELECT id, tenant_id FROM documents
                        WHERE status = 'PENDING'
                           OR (status = 'PROCESSING' AND updated_at < ?)
                        ORDER BY created_at
                        LIMIT ?
                        """)
                .param(OffsetDateTime.ofInstant(staleBefore, ZoneOffset.UTC))
                .param(limit)
                .query((rs, rowNum) -> new ClaimableJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class)))
                .list();
    }

    /**
     * Атомарно забирает документ в работу: переводит в {@code PROCESSING}, только если он
     * всё ещё {@code PENDING} или зависший {@code PROCESSING}. Гарантирует, что один документ
     * не обработают дважды (поллер vs нудж от загрузки, несколько тиков поллера).
     *
     * @return true, если claim удался именно этим вызовом
     */
    @Transactional
    public boolean tryClaim(UUID documentId, Instant staleBefore) {
        int updated = jdbcClient.sql("""
                        UPDATE documents
                        SET status = 'PROCESSING', updated_at = now()
                        WHERE id = ?
                          AND (status = 'PENDING' OR (status = 'PROCESSING' AND updated_at < ?))
                        """)
                .param(documentId)
                .param(OffsetDateTime.ofInstant(staleBefore, ZoneOffset.UTC))
                .update();
        return updated == 1;
    }
}
