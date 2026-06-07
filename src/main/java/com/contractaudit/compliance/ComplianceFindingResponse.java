package com.contractaudit.compliance;

import com.contractaudit.risk.RiskSeverity;

import java.util.UUID;

/** Представление finding'а сверки для API. */
public record ComplianceFindingResponse(UUID id, ComplianceFindingType type, RiskSeverity severity,
                                        UUID policyId, String clauseRef, String quote, String explanation) {

    public static ComplianceFindingResponse from(ComplianceFinding finding) {
        return new ComplianceFindingResponse(finding.getId(), finding.getFindingType(),
                finding.getSeverity(), finding.getPolicyId(), finding.getClauseRef(),
                finding.getQuote(), finding.getExplanation());
    }
}
