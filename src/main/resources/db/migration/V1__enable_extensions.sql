-- pgvector — векторный тип и операторы (<=> косинусное расстояние).
-- Образ pgvector/pgvector содержит расширение; в managed-Postgres его надо разрешить.
CREATE EXTENSION IF NOT EXISTS vector;

-- gen_random_uuid() для первичных ключей.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
