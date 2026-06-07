package com.contractaudit.chunk;

import java.util.UUID;

/**
 * Результат семантического поиска: чанк + косинусное расстояние до запроса.
 * Меньше {@code distance} — ближе по смыслу.
 *
 * @param id         id чанка
 * @param documentId документ-источник
 * @param chunkText  текст чанка
 * @param clauseRef  ссылка на пункт (для цитат в найденных рисках)
 * @param distance   косинусное расстояние (оператор {@code <=>})
 */
public record ChunkMatch(UUID id, UUID documentId, String chunkText, String clauseRef, double distance) {
}
