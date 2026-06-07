package com.contractaudit.document.processing;

/**
 * Результат chunking: фрагмент текста с порядковым номером и (если распознана) ссылкой
 * на пункт договора.
 *
 * @param index     порядковый номер чанка в документе
 * @param text      текст фрагмента
 * @param clauseRef номер пункта, к которому относится фрагмент (напр. «7.2»), либо null
 */
public record TextChunk(int index, String text, String clauseRef) {
}
