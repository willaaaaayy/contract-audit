package com.contractaudit.risk;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Схема structured output для Risk Scanner — то, что LLM обязан вернуть JSON'ом
 * (через {@code BeanOutputConverter}). См. docs/retrieval-design.md (Risk Scanner).
 */
public record RiskScanResult(List<RiskItem> risks) {

    /**
     * Один найденный риск. {@code clauseRef} + {@code quote} обязательны для проверяемости.
     */
    public record RiskItem(
            @JsonPropertyDescription("Тип риска: штрафная санкция, срок, сумма, ответственность и т.п.")
            String riskType,
            @JsonPropertyDescription("Серьёзность: LOW, MEDIUM, HIGH или CRITICAL")
            String severity,
            @JsonPropertyDescription("Ссылка на пункт договора, например «7.2»")
            String clauseRef,
            @JsonPropertyDescription("Точная цитата из текста договора, обосновывающая риск")
            String quote,
            @JsonPropertyDescription("Краткое объяснение, почему это риск")
            String explanation) {
    }
}
