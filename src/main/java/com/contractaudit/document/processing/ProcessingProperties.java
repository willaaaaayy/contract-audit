package com.contractaudit.document.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры пайплайна обработки документов.
 *
 * @param chunkMaxChars     целевой максимум символов в чанке (пункты не разрываются, пока влезают)
 * @param embedBatchSize    сколько чанков эмбеддить за один вызов модели
 * @param staleAfterSeconds через сколько секунд «зависший» {@code PROCESSING} считается брошенным
 *                          и может быть переобработан (восстановление после краша)
 * @param pollBatchSize     сколько документов поллер забирает за тик
 */
@ConfigurationProperties(prefix = "processing")
public record ProcessingProperties(int chunkMaxChars, int embedBatchSize,
                                   int staleAfterSeconds, int pollBatchSize) {

    public ProcessingProperties {
        if (chunkMaxChars <= 0 || embedBatchSize <= 0) {
            throw new IllegalArgumentException("processing.* params must be positive");
        }
        if (staleAfterSeconds <= 0) {
            staleAfterSeconds = 300;
        }
        if (pollBatchSize <= 0) {
            pollBatchSize = 10;
        }
    }
}
