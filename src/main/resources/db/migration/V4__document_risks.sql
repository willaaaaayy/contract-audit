-- Найденные ИИ риски по документу. См. docs/retrieval-design.md (Risk Scanner):
-- каждый риск ОБЯЗАН ссылаться на место в договоре (clause_ref + quote), иначе юрист
-- не сможет проверить вывод.
CREATE TABLE document_risks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),       -- изоляция арендатора
    document_id UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    risk_type   TEXT        NOT NULL,                              -- напр. «штрафная санкция», «срок»
    severity    TEXT        NOT NULL,                              -- LOW/MEDIUM/HIGH/CRITICAL
    clause_ref  TEXT,                                              -- «п. 7.2»
    quote       TEXT        NOT NULL,                              -- цитата из договора (проверяемость)
    explanation TEXT        NOT NULL,                              -- почему это риск
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_risks_tenant ON document_risks(tenant_id);
CREATE INDEX idx_risks_document ON document_risks(document_id);
