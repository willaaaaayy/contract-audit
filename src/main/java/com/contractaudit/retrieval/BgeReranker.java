package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Боевой cross-encoder реранкер (этап 2). Ходит в self-host сервис cross-encoder —
 * HuggingFace Text Embeddings Inference (TEI) с моделью вроде {@code bge-reranker-v2-m3} —
 * по эндпоинту {@code POST /rerank}. См. docs/retrieval-design.md §5.
 *
 * <p>В отличие от bi-encoder/косинуса, cross-encoder видит пару (запрос, текст) вместе и
 * различает почти идентичные формулировки («несёт» vs «не несёт ответственность»), критичные
 * для договоров.
 */
public class BgeReranker implements Reranker {

    private final RestClient restClient;

    public BgeReranker(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<ChunkMatch> rerank(String query, List<ChunkMatch> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> texts = candidates.stream().map(ChunkMatch::chunkText).toList();
        RerankItem[] ranked = restClient.post()
                .uri("/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RerankRequest(query, texts))
                .retrieve()
                .body(RerankItem[].class);

        if (ranked == null) {
            return candidates.stream().limit(topK).toList();
        }
        // TEI возвращает по убыванию score, но сортируем защитно и привязываем по index.
        return Arrays.stream(ranked)
                .sorted(Comparator.comparingDouble(RerankItem::score).reversed())
                .limit(topK)
                .map(item -> candidates.get(item.index()))
                .toList();
    }

    /** Тело запроса к TEI {@code /rerank}. */
    private record RerankRequest(String query, List<String> texts) {
    }

    /** Элемент ответа: индекс исходного текста и его релевантность. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RerankItem(int index, double score) {
    }
}
