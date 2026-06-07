-- workspace-slug арендатора — человекочитаемый идентификатор для входа (как в Slack).
-- Решает bootstrap логина: по slug находим арендатора (глобально), затем поиск пользователя
-- идёт уже в его контексте (tenant-scoped).
ALTER TABLE tenants ADD COLUMN slug TEXT;

-- Уникальность slug. NULL'ы в Postgres различны, поэтому старые строки без slug не мешают.
CREATE UNIQUE INDEX uq_tenants_slug ON tenants(slug);
