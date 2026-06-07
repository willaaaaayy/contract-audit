package com.contractaudit.policy;

import java.util.UUID;

/**
 * Политика со всеми данными, нужными Compliance Checker'у, включая эмбеддинг — им ищем
 * релевантные пункты договора.
 *
 * @param id        id политики
 * @param title     название
 * @param text      текст политики (идёт в LLM для оценки противоречия)
 * @param mandatory обязательна ли
 * @param embedding вектор текста политики
 */
public record PolicyForCheck(UUID id, String title, String text, boolean mandatory, float[] embedding) {
}
