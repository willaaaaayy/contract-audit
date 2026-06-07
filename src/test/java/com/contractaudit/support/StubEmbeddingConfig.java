package com.contractaudit.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Подменяет реальный (OpenAI) {@link EmbeddingModel} стабом во всех точках внедрения.
 * {@code @Primary} разрешает неоднозначность в пользу стаба, не убирая боевой бин из контекста.
 */
@TestConfiguration
public class StubEmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel stubEmbeddingModel() {
        return new StubEmbeddingModel();
    }
}
