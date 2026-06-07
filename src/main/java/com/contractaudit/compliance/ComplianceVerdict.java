package com.contractaudit.compliance;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured output вердикта LLM: противоречит ли пункт договора политике.
 */
public record ComplianceVerdict(
        @JsonPropertyDescription("true, если пункт договора противоречит политике")
        boolean contradicts,
        @JsonPropertyDescription("Серьёзность: LOW, MEDIUM, HIGH или CRITICAL")
        String severity,
        @JsonPropertyDescription("Ссылка на пункт договора, например «7.2»")
        String clauseRef,
        @JsonPropertyDescription("Точная цитата из договора, обосновывающая противоречие")
        String quote,
        @JsonPropertyDescription("Краткое объяснение противоречия")
        String explanation) {
}
