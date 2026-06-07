package com.contractaudit.document;

import com.contractaudit.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранит сырые байты PDF. Tenant-scoped: {@code tenant_id} всегда из {@link TenantContext},
 * чужой blob недоступен.
 */
@Repository
public class BlobRepository {

    private final JdbcClient jdbcClient;

    public BlobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public void save(UUID documentId, byte[] content) {
        jdbcClient.sql("""
                        INSERT INTO document_blobs (document_id, tenant_id, content)
                        VALUES (?, ?, ?)
                        ON CONFLICT (document_id) DO UPDATE SET content = EXCLUDED.content
                        """)
                .param(documentId)
                .param(TenantContext.require())
                .param(content)
                .update();
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> load(UUID documentId) {
        return jdbcClient.sql("""
                        SELECT content FROM document_blobs
                        WHERE tenant_id = ? AND document_id = ?
                        """)
                .param(TenantContext.require())
                .param(documentId)
                .query(byte[].class)
                .optional();
    }
}
