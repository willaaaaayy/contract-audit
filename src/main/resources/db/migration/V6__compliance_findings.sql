-- Результаты сверки договора с политиками компании (Compliance Checker).
-- finding_type: CONTRADICTION (пункт противоречит политике) или
--               MISSING_REQUIRED (обязательная политика не отражена в договоре).
CREATE TABLE compliance_findings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL REFERENCES tenants(id),
    document_id  UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    policy_id    UUID        REFERENCES policies(id) ON DELETE SET NULL,
    finding_type TEXT        NOT NULL,
    severity     TEXT        NOT NULL,
    clause_ref   TEXT,                          -- для CONTRADICTION
    quote        TEXT,                          -- цитата (у MISSING_REQUIRED отсутствует)
    explanation  TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_findings_tenant ON compliance_findings(tenant_id);
CREATE INDEX idx_findings_document ON compliance_findings(document_id);
