package com.contractaudit.negotiation;

import java.util.List;

/** Структурированный вывод LLM с предложениями по правкам пунктов. */
public record SuggestionResult(List<Item> suggestions) {

    /**
     * @param clauseRef ссылка на пункт
     * @param original  исходная формулировка
     * @param suggested предложенная формулировка
     * @param rationale обоснование правки
     */
    public record Item(String clauseRef, String original, String suggested, String rationale) {
    }
}
