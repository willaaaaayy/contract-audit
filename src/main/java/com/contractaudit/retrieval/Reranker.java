package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;

import java.util.List;

/**
 * Этап 2 извлечения: пересортировка кандидатов этапа 1 и отбор top-k.
 * См. docs/retrieval-design.md §5.
 *
 * <p>Боевая реализация — cross-encoder (напр. self-host bge-reranker-v2-m3), который
 * различает почти идентичные формулировки («несёт» vs «не несёт ответственность»), где
 * bi-encoder/косинус слепнет. Дефолтная реализация ({@link DistanceSortReranker}) —
 * заглушка-митигация §3, пока cross-encoder не подключён.
 */
public interface Reranker {

    /**
     * @param query      исходный текстовый запрос (нужен cross-encoder'у)
     * @param candidates кандидаты этапа 1 (порядок может быть нестрогим из-за relaxed_order)
     * @param topK       сколько вернуть после пересортировки
     * @return ровно до {@code topK} результатов в порядке убывания релевантности
     */
    List<ChunkMatch> rerank(String query, List<ChunkMatch> candidates, int topK);
}
