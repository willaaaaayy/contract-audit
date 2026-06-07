-- Библиотека внутренних политик компании (для Compliance Checker).
-- См. docs/retrieval-design.md (Compliance Checker): сверяем пункты договора с этими политиками.
-- Tenant-scoped: политики компании А недоступны компании Б.
CREATE TABLE policies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),
    title       TEXT         NOT NULL,
    policy_text TEXT         NOT NULL,
    mandatory   BOOLEAN      NOT NULL DEFAULT false,   -- обязательная → её отсутствие в договоре = риск
    embedding   VECTOR(1536) NOT NULL,                 -- вектор текста политики
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_policies_tenant ON policies(tenant_id);
-- HNSW-индекс на policies НЕ нужен: проверка идёт policy-centric — по вектору политики
-- ищем релевантные пункты в document_chunks (там индекс уже есть), а не наоборот.
