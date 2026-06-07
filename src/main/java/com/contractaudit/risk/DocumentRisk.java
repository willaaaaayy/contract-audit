package com.contractaudit.risk;

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
 * Риск, найденный ИИ в договоре. {@code @TenantId} обеспечивает изоляцию арендатора.
 * Поля {@code clauseRef} + {@code quote} делают вывод проверяемым — юрист видит, на каком
 * месте договора основан риск.
 */
@Entity
@Table(name = "document_risks")
public class DocumentRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "risk_type", nullable = false)
    private String riskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskSeverity severity;

    @Column(name = "clause_ref")
    private String clauseRef;

    @Column(nullable = false)
    private String quote;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DocumentRisk() {
    }

    public DocumentRisk(UUID documentId, String riskType, RiskSeverity severity,
                        String clauseRef, String quote, String explanation) {
        this.documentId = documentId;
        this.riskType = riskType;
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

    public String getRiskType() {
        return riskType;
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
