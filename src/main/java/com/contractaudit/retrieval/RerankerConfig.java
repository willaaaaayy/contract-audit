package com.contractaudit.retrieval;

import com.contractaudit.chunk.ChunkMatch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Comparator;

/**
 * Выбор реализации {@link Reranker} (этап 2 извлечения, docs/retrieval-design.md §5):
 *
 * <ul>
 *   <li>если задан {@code retrieval.rerank.url} — боевой {@link BgeReranker} (cross-encoder
 *       через TEI);</li>
 *   <li>иначе — заглушка: пересортировка по косинусному расстоянию (митигация relaxed_order
 *       из §3), даёт точный порядок без cross-encoder.</li>
 * </ul>
 *
 * <p>Bge-бин объявлен раньше, поэтому {@link ConditionalOnMissingBean} у заглушки видит его и
 * не создаёт дубль, когда cross-encoder сконфигурирован.
 */
@Configuration
public class RerankerConfig {

    @Bean
    @ConditionalOnProperty(prefix = "retrieval.rerank", name = "url")
    public Reranker bgeReranker(RetrievalProperties properties, RestClient.Builder restClientBuilder) {
        RestClient client = restClientBuilder.baseUrl(properties.rerank().url()).build();
        return new BgeReranker(client);
    }

    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    public Reranker distanceSortReranker() {
        return (query, candidates, topK) -> candidates.stream()
                .sorted(Comparator.comparingDouble(ChunkMatch::distance))
                .limit(topK)
                .toList();
    }
}
