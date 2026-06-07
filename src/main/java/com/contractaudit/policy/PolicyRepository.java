package com.contractaudit.policy;

import com.contractaudit.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Доступ к политикам компании. Как и {@link com.contractaudit.chunk.DocumentChunkRepository},
 * содержит нативный pgvector-SQL, поэтому {@code tenant_id} всюду берётся из
 * {@link TenantContext} и не приходит параметром — изоляция не может быть опущена.
 */
@Repository
public class PolicyRepository {

    private final JdbcClient jdbcClient;

    public PolicyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public UUID save(NewPolicy policy) {
        return jdbcClient.sql("""
                        INSERT INTO policies (tenant_id, title, policy_text, mandatory, embedding)
                        VALUES (?, ?, ?, ?, ?::vector)
                        RETURNING id
                        """)
                .param(TenantContext.require())
                .param(policy.title())
                .param(policy.text())
                .param(policy.mandatory())
                .param(toVectorLiteral(policy.embedding()))
                .query(UUID.class)
                .single();
    }

    /** Все политики арендатора с эмбеддингами — вход для Compliance Checker. */
    @Transactional(readOnly = true)
    public List<PolicyForCheck> findAllForCheck() {
        return jdbcClient.sql("""
                        SELECT id, title, policy_text, mandatory, embedding::text AS embedding_text
                        FROM policies
                        WHERE tenant_id = ?
                        ORDER BY created_at
                        """)
                .param(TenantContext.require())
                .query((rs, rowNum) -> new PolicyForCheck(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("policy_text"),
                        rs.getBoolean("mandatory"),
                        parseVector(rs.getString("embedding_text"))))
                .list();
    }

    /** Список политик для API (без эмбеддингов). */
    @Transactional(readOnly = true)
    public List<PolicySummary> list() {
        return jdbcClient.sql("""
                        SELECT id, title, mandatory
                        FROM policies
                        WHERE tenant_id = ?
                        ORDER BY created_at
                        """)
                .param(TenantContext.require())
                .query((rs, rowNum) -> new PolicySummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getBoolean("mandatory")))
                .list();
    }

    /** Краткое представление политики для списков. */
    public record PolicySummary(UUID id, String title, boolean mandatory) {
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static float[] parseVector(String literal) {
        String body = literal.substring(1, literal.length() - 1);   // срезаем [ и ]
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
