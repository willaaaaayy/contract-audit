-- HNSW-индекс для семантического поиска. См. docs/retrieval-design.md §6.
--
-- vector_cosine_ops — оператор <=> (косинусное расстояние), совпадает с метрикой,
-- которой text-embedding-3-small сравнивает векторы.
--
-- Параметры построения подняты ВЫШЕ дефолтов (m=16, ef_construction=64) намеренно:
-- больший базовый recall даёт запас под мультиарендный over-filtering ещё до
-- iterative_scan (§2, §6). Цена — память (m) и время стройки (ef_construction),
-- но ef_construction в рантайме бесплатен.
--
-- Замечание по эксплуатации: на больших объёмах индекс быстрее строить ПОСЛЕ
-- bulk-load при повышенном maintenance_work_mem. Для первичной схемы это неважно —
-- таблица пустая.
CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 32, ef_construction = 128);
