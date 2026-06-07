package com.contractaudit.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры двухэтапного извлечения. См. docs/retrieval-design.md §2, §3, §5.
 *
 * @param vector параметры этапа 1 (pgvector / HNSW)
 * @param rerank параметры этапа 2 (реранкер)
 */
@ConfigurationProperties(prefix = "retrieval")
public record RetrievalProperties(Vector vector, Rerank rerank) {

    /**
     * @param overFetch      сколько кандидатов тянуть на этапе 1 (N > top-k под реранкер, §5)
     * @param efSearch       hnsw.ef_search — ширина фронта поиска (§2)
     * @param iterativeScan  hnsw.iterative_scan: {@code relaxed_order} | {@code strict_order} | {@code off} (§2, §3)
     * @param maxScanTuples  hnsw.max_scan_tuples — предохранитель от скана всей таблицы (§2)
     */
    public record Vector(int overFetch, int efSearch, String iterativeScan, int maxScanTuples) {

        /** Допускаем только известные значения — строка идёт в SET LOCAL без bind-параметров. */
        public Vector {
            if (!java.util.Set.of("relaxed_order", "strict_order", "off").contains(iterativeScan)) {
                throw new IllegalArgumentException("Unknown hnsw.iterative_scan value: " + iterativeScan);
            }
            if (overFetch <= 0 || efSearch <= 0 || maxScanTuples <= 0) {
                throw new IllegalArgumentException("retrieval.vector numeric params must be positive");
            }
        }
    }

    /**
     * @param enabled включён ли этап реранкинга
     * @param topK    сколько результатов оставить после пересортировки (уходит в LLM)
     * @param url     базовый URL сервиса cross-encoder (TEI {@code /rerank}); если не задан,
     *                используется заглушка distance-sort (см. {@code RerankerConfig})
     */
    public record Rerank(boolean enabled, int topK, String url) {
    }
}
