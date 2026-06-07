package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;
import com.contractaudit.chunk.DocumentChunkRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Семантический поиск по контрактам текущего арендатора — двухэтапный пайплайн из
 * docs/retrieval-design.md §5:
 *
 * <pre>
 *   запрос → embed → этап 1: pgvector top-N (over-fetch) → этап 2: реранкер top-k
 * </pre>
 *
 * <p>Изоляция по арендатору обеспечивается ниже, в {@link DocumentChunkRepository}
 * (всегда {@code WHERE tenant_id}), поэтому здесь о ней думать не нужно.
 */
@Service
public class SemanticSearchService {

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final Reranker reranker;
    private final RetrievalProperties props;

    public SemanticSearchService(EmbeddingModel embeddingModel,
                                 DocumentChunkRepository chunkRepository,
                                 Reranker reranker,
                                 RetrievalProperties props) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.reranker = reranker;
        this.props = props;
    }

    /**
     * Находит наиболее релевантные запросу чанки договоров текущего арендатора.
     *
     * @param query запрос на естественном языке («найти все договоры, где мы платим за доставку»)
     * @return до {@code retrieval.rerank.top-k} результатов по убыванию релевантности
     */
    public List<ChunkMatch> search(String query) {
        // Этап 0: эмбеддинг запроса той же моделью, что и чанки (метрика обязана совпадать, §4).
        float[] queryVector = embeddingModel.embed(query);

        // Этап 1: широкая сеть из pgvector — over-fetch под реранкер.
        List<ChunkMatch> candidates = chunkRepository.searchSimilar(queryVector, props.vector().overFetch());

        int topK = props.rerank().topK();
        if (props.rerank().enabled()) {
            // Этап 2: cross-encoder пересортировывает и отбирает top-k.
            return reranker.rerank(query, candidates, topK);
        }
        // Без реранкера — досортировка по расстоянию (митигация relaxed_order, §3).
        return candidates.stream()
                .sorted(Comparator.comparingDouble(ChunkMatch::distance))
                .limit(topK)
                .toList();
    }
}
