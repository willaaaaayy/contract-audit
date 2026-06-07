package com.contractaudit.compliance;

import com.contractaudit.risk.RiskSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Результат сверки договора с одной политикой компании. {@code @TenantId} обеспечивает
 * изоляцию арендатора.
 */
@Entity
@Table(name = "compliance_findings")
public class ComplianceFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "policy_id")
    private UUID policyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_type", nullable = false)
    private ComplianceFindingType findingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskSeverity severity;

    @Column(name = "clause_ref")
    private String clauseRef;

    @Column
    private String quote;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ComplianceFinding() {
    }

    public ComplianceFinding(UUID documentId, UUID policyId, ComplianceFindingType findingType,
                             RiskSeverity severity, String clauseRef, String quote, String explanation) {
        this.documentId = documentId;
        this.policyId = policyId;
        this.findingType = findingType;
        this.severity = severity;
        this.clauseRef = clauseRef;
        this.quote = quote;
        this.explanation = explanation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public ComplianceFindingType getFindingType() {
        return findingType;
    }

    public RiskSeverity getSeverity() {
        return severity;
    }

    public String getClauseRef() {
        return clauseRef;
    }

    public String getQuote() {
        return quote;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
