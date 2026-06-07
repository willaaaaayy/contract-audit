package com.contractaudit.policy;

/**
 * Политика для вставки: текст + готовый эмбеддинг. Без id/tenant_id — их проставляет
 * репозиторий ({@code tenant_id} из контекста арендатора).
 *
 * @param title     краткое название политики
 * @param text      текст политики
 * @param mandatory обязательна ли (её отсутствие в договоре — риск)
 * @param embedding вектор текста политики, размерность 1536
 */
public record NewPolicy(String title, String text, boolean mandatory, float[] embedding) {
}
