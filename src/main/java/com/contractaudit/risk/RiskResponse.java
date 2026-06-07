package com.contractaudit.risk;

import java.util.UUID;

/** Представление найденного риска для API. */
public record RiskResponse(UUID id, String riskType, RiskSeverity severity,
                           String clauseRef, String quote, String explanation) {

    public static RiskResponse from(DocumentRisk risk) {
        return new RiskResponse(risk.getId(), risk.getRiskType(), risk.getSeverity(),
                risk.getClauseRef(), risk.getQuote(), risk.getExplanation());
    }
}
