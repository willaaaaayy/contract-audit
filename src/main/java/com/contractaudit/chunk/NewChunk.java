package com.contractaudit.chunk;

import java.util.UUID;

/**
 * Чанк для вставки: текст + готовый эмбеддинг. Без id/tenant_id — id генерирует БД,
 * а {@code tenant_id} проставляет репозиторий из {@link com.contractaudit.tenant.TenantContext}.
 *
 * @param documentId документ, которому принадлежит чанк
 * @param chunkIndex порядковый номер чанка в документе
 * @param text       текст чанка
 * @param clauseRef  ссылка на пункт договора (напр. «п. 7.2»), может быть null
 * @param embedding  вектор эмбеддинга, размерность 1536 (text-embedding-3-small)
 */
public record NewChunk(UUID documentId, int chunkIndex, String text, String clauseRef, float[] embedding) {
}
