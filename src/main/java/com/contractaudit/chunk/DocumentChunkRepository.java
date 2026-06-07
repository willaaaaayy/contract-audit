package com.contractaudit.chunk;

import com.contractaudit.retrieval.RetrievalProperties;
import com.contractaudit.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Доступ к {@code document_chunks} — единственное место в коде с нативным векторным SQL.
 *
 * <p><b>Инвариант изоляции (docs/retrieval-design.md §2):</b> ни одна операция здесь не
 * принимает {@code tenant_id} параметром. Он всегда берётся из
 * {@link TenantContext#require()} и подставляется в каждый запрос — забыть его или
 * подменить из вызывающего кода невозможно. Это компенсирует то, что чанки (в отличие
 * от JPA-сущностей с {@code @TenantId}) ходят мимо Hibernate.
 */
@Repository
public class DocumentChunkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO document_chunks
                (tenant_id, document_id, chunk_index, chunk_text, clause_ref, embedding)
            VALUES (?, ?, ?, ?, ?, ?::vector)
            """;

    // ВСЕГДА с WHERE tenant_id. Порядок по косинусному расстоянию (<=>).
    private static final String SEARCH_SQL = """
            SELECT id, document_id, chunk_text, clause_ref, embedding <=> ?::vector AS distance
            FROM document_chunks
            WHERE tenant_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final RetrievalProperties.Vector vectorProps;

    public DocumentChunkRepository(JdbcClient jdbcClient,
                                   JdbcTemplate jdbcTemplate,
                                   RetrievalProperties retrievalProperties) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorProps = retrievalProperties.vector();
    }

    /** Удаляет все чанки документа (tenant-scoped). Делает переобработку идемпотентной. */
    @Transactional
    public void deleteByDocument(UUID documentId) {
        jdbcClient.sql("DELETE FROM document_chunks WHERE tenant_id = ? AND document_id = ?")
                .param(TenantContext.require())
                .param(documentId)
                .update();
    }

    /** Пакетная вставка чанков текущего арендатора. */
    @Transactional
    public void saveAll(List<NewChunk> chunks) {
        UUID tenantId = TenantContext.require();
        jdbcTemplate.batchUpdate(INSERT_SQL, chunks, chunks.size(), (ps, chunk) -> {
            ps.setObject(1, tenantId);
            ps.setObject(2, chunk.documentId());
            ps.setInt(3, chunk.chunkIndex());
            ps.setString(4, chunk.text());
            ps.setString(5, chunk.clauseRef());
            ps.setString(6, toVectorLiteral(chunk.embedding()));
        });
    }

    /**
     * Этап 1 двухэтапного извлечения: top-N ближайших чанков ТЕКУЩЕГО арендатора.
     *
     * <p>{@code @Transactional} обязателен: {@code SET LOCAL} действует в пределах
     * транзакции и применяется к тому же соединению, что и последующий запрос.
     * Настройки HNSW (ef_search, iterative_scan, max_scan_tuples) защищают от
     * over-filtering на мелких арендаторах (§2).
     *
     * @param queryVector эмбеддинг поискового запроса
     * @param limit       сколько кандидатов вернуть (обычно {@code retrieval.vector.over-fetch})
     */
    @Transactional(readOnly = true)
    public List<ChunkMatch> searchSimilar(float[] queryVector, int limit) {
        applyHnswSessionSettings();
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcClient.sql(SEARCH_SQL)
                .param(vectorLiteral)             // distance в SELECT
                .param(TenantContext.require())   // WHERE tenant_id — НИКОГДА не из параметра метода
                .param(vectorLiteral)             // ORDER BY
                .param(limit)
                .query((rs, rowNum) -> new ChunkMatch(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("chunk_text"),
                        rs.getString("clause_ref"),
                        rs.getDouble("distance")))
                .list();
    }

    // Поиск в пределах одного документа (для Compliance Checker). ВСЕГДА с tenant_id.
    private static final String SEARCH_IN_DOCUMENT_SQL = """
            SELECT id, document_id, chunk_text, clause_ref, embedding <=> ?::vector AS distance
            FROM document_chunks
            WHERE tenant_id = ? AND document_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    /**
     * Этап 1, ограниченный одним документом: ближайшие к запросу чанки данного документа
     * текущего арендатора. Используется Compliance Checker'ом — ищем в договоре пункты,
     * релевантные политике.
     */
    @Transactional(readOnly = true)
    public List<ChunkMatch> searchSimilarInDocument(float[] queryVector, UUID documentId, int limit) {
        applyHnswSessionSettings();
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcClient.sql(SEARCH_IN_DOCUMENT_SQL)
                .param(vectorLiteral)             // distance
                .param(TenantContext.require())   // WHERE tenant_id
                .param(documentId)                // WHERE document_id
                .param(vectorLiteral)             // ORDER BY
                .param(limit)
                .query((rs, rowNum) -> new ChunkMatch(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("chunk_text"),
                        rs.getString("clause_ref"),
                        rs.getDouble("distance")))
                .list();
    }

    /**
     * Текст документа, собранный из чанков по порядку. Tenant-scoped: {@code tenant_id}
     * из контекста — чужой документ не вернётся. Используется Risk Scanner'ом.
     */
    @Transactional(readOnly = true)
    public List<String> findChunkTextsByDocument(UUID documentId) {
        return jdbcClient.sql("""
                        SELECT chunk_text FROM document_chunks
                        WHERE tenant_id = ? AND document_id = ?
                        ORDER BY chunk_index
                        """)
                .param(TenantContext.require())
                .param(documentId)
                .query(String.class)
                .list();
    }

    /**
     * Настройки HNSW на текущую транзакцию. Значения берутся из валидированного конфига
     * ({@link RetrievalProperties.Vector}), поэтому их безопасно инлайнить в SET LOCAL
     * (bind-параметры в SET не поддерживаются).
     */
    private void applyHnswSessionSettings() {
        jdbcTemplate.execute("SET LOCAL hnsw.ef_search = " + vectorProps.efSearch());
        jdbcTemplate.execute("SET LOCAL hnsw.iterative_scan = " + vectorProps.iterativeScan());
        if (!"off".equals(vectorProps.iterativeScan())) {
            jdbcTemplate.execute("SET LOCAL hnsw.max_scan_tuples = " + vectorProps.maxScanTuples());
        }
    }

    /** float[] → pgvector-литерал вида {@code [0.1,0.2,...]}. */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
