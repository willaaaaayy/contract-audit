-- Базовая схема мультиарендного приложения. См. docs/retrieval-design.md §1.
-- Инвариант: tenant_id присутствует в КАЖДОЙ прикладной таблице и проставляется
-- только сервером (никогда из тела запроса).

CREATE TABLE tenants (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL REFERENCES tenants(id),
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- email уникален В ПРЕДЕЛАХ tenant, а не глобально:
    -- admin@acme.com и admin@globex.com сосуществуют.
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id),
    filename    TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'PENDING',   -- PENDING/PROCESSING/DONE/FAILED
    uploaded_by UUID        REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_tenant ON documents(tenant_id);

-- Чанки текста + эмбеддинги. ГЛАВНЫЙ риск изоляции живёт здесь (см. §2):
-- ни один векторный запрос не должен идти без WHERE tenant_id.
CREATE TABLE document_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenants(id),   -- ОБЯЗАТЕЛЬНО и здесь
    document_id UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER      NOT NULL,                          -- порядок чанка в документе
    chunk_text  TEXT         NOT NULL,
    clause_ref  TEXT,                                           -- «п. 7.2» — для цитат в рисках
    embedding   VECTOR(1536) NOT NULL,                          -- text-embedding-3-small
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- B-tree по tenant_id: сначала отсекаем чужие строки, потом векторный поиск (§2, Вариант А).
CREATE INDEX idx_chunks_tenant ON document_chunks(tenant_id);
CREATE INDEX idx_chunks_document ON document_chunks(document_id);
