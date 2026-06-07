package com.contractaudit.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Конфигурация локального (self-hosted) inference. Активна только в профиле {@code local},
 * где провайдером выбран Ollama (см. {@code application-local.yml}).
 *
 * <p>Оборачивает автоконфигурированный {@link OllamaEmbeddingModel} в {@link PaddingEmbeddingModel},
 * чтобы привести размерность локальной модели (bge-m3 → 1024) к схеме {@code VECTOR(1536)} без
 * миграций. Обёртка помечена {@code @Primary} — её получают пайплайн обработки, семантический
 * поиск и сервис политик, инжектящие интерфейс {@link EmbeddingModel}.
 *
 * <p>ChatModel подменять не нужно: в профиле {@code local} активен только Ollama-чат, а сервисы
 * (Risk Scanner, Compliance) инжектят интерфейс {@code ChatModel} — провайдер прозрачен.
 */
@Configuration
@Profile("local")
public class LocalAiConfig {

    @Bean
    @Primary
    public EmbeddingModel paddingEmbeddingModel(
            OllamaEmbeddingModel delegate,
            @Value("${app.embedding.target-dimensions:1536}") int targetDimensions) {
        return new PaddingEmbeddingModel(delegate, targetDimensions);
    }
}
