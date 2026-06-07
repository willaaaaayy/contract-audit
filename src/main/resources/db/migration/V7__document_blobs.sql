-- Сырые байты загруженного PDF — для долговечности пайплайна.
-- Загрузка коммитит документ (PENDING) + blob; обработка читает blob отсюда, поэтому
-- переживает рестарт приложения (поллер заберёт незавершённые документы заново).
CREATE TABLE document_blobs (
    document_id UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id   UUID  NOT NULL REFERENCES tenants(id),
    content     BYTEA NOT NULL
);

-- На больших объёмах blob лучше держать в object storage (S3/MinIO), а здесь — только
-- ссылку. Для MVP bytea в Postgres достаточно (лимит загрузки 25 МБ).
