# Retrieval & Multi-tenancy Design

Проектный документ по слою хранения и семантического поиска для AI-ассистента
аудита B2B-контрактов. Покрывает изоляцию данных (multi-tenancy), векторный поиск
на pgvector/HNSW, проблему over-filtering, ранжирование и тестовую стратегию.

Стек: Java 21+, Spring Boot 3.x, Spring AI, Spring Security + JWT, PostgreSQL + pgvector.

---

## Содержание

1. [Multi-tenancy](#1-multi-tenancy)
2. [Векторный поиск и over-filtering в HNSW](#2-векторный-поиск-и-over-filtering-в-hnsw)
3. [Порядок результатов: `relaxed_order` vs `strict_order`](#3-порядок-результатов-relaxed_order-vs-strict_order)
4. [Тестовая стратегия: leak-тесты и recall@k](#4-тестовая-стратегия-leak-тесты-и-recallk)
5. [Реранкер](#5-реранкер)
6. [Построение HNSW: `m`, `ef_construction`, стохастичность](#6-построение-hnsw-m-ef_construction-стохастичность)
7. [Итоговые рекомендации](#7-итоговые-рекомендации)

---

## 1. Multi-tenancy

**Решать первым, до всего остального.** Смена модели изоляции позже = переписать всё.

### Выбор модели для MVP

| Подход | Изоляция | Сложность | Когда |
|---|---|---|---|
| **Shared DB + `tenant_id` в каждой строке** | Логическая (фильтр в коде) | Низкая | ✅ MVP |
| Shared DB + Postgres RLS | На уровне БД | Средняя | Enterprise-аудит безопасности |
| DB-per-tenant | Физическая | Высокая | Банки/госы с требованием физ. разделения |

Старт: `tenant_id` + **Hibernate `@Filter`**. JWT несёт `tenant_id` в claims, но сам
изоляцию **не** обеспечивает — это лишь источник идентификатора.

### Путь запроса

```
JWT(tenant_id=A) → Security Filter извлекает tenant_id
                 → TenantContext (ThreadLocal)
                 → Service слой
                 → Hibernate автоматически добавляет AND tenant_id = 'A'
                 → PostgreSQL (+ pgvector)
```

`tenant_id` извлекается **один раз** на входе и далее **автоматически** подмешивается
во все запросы. Разработчик не должен помнить про него в каждом методе — забудет = утечка.

### Схема БД

```sql
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL,
    -- email уникален В ПРЕДЕЛАХ tenant, а не глобально
    UNIQUE (tenant_id, email)
);

CREATE TABLE documents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    filename    TEXT NOT NULL,
    status      TEXT NOT NULL,          -- PENDING/PROCESSING/DONE/FAILED
    uploaded_by UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_documents_tenant ON documents(tenant_id);

-- Чанки текста + векторы. ГЛАВНЫЙ риск изоляции живёт здесь.
CREATE TABLE document_chunks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),   -- ОБЯЗАТЕЛЬНО здесь тоже
    document_id UUID NOT NULL REFERENCES documents(id),
    chunk_text  TEXT NOT NULL,
    clause_ref  TEXT,                   -- «п. 7.2», для цитат в рисках
    embedding   VECTOR(1536)            -- размерность под embedding-модель
);
```

### Извлечение tenant_id из JWT

```json
{
  "sub": "user-uuid",
  "tenant_id": "acme-tenant-uuid",
  "role": "LEGAL_ANALYST",
  "exp": 1733500000
}
```

```java
public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    public static void set(UUID id) { CURRENT.set(id); }
    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) throw new IllegalStateException("No tenant in context");
        return id;
    }
    public static void clear() { CURRENT.remove(); }  // ОБЯЗАТЕЛЬНО в finally
}
```

> ⚠️ **Ловушка ThreadLocal:** пул потоков переиспользует потоки. Забыли `clear()` —
> следующий запрос унаследует чужой `tenant_id`. Чистка в `finally` фильтра обязательна.
> С virtual threads (Java 21) ThreadLocal работает, но присмотритесь к `ScopedValue`.

### Автофильтрация через Hibernate `@Filter`

```java
@Entity
@Table(name = "documents")
@FilterDef(name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Document {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void assignTenant() {
        if (this.tenantId == null) this.tenantId = TenantContext.require();
    }
}
```

```java
// включаем фильтр на каждой сессии (перехватчик / аспект)
Session session = entityManager.unwrap(Session.class);
session.enableFilter("tenantFilter")
       .setParameter("tenantId", TenantContext.require());
```

`@Filter` действует **только на чтение**. На запись `tenant_id` ставится сервером
через `@PrePersist` — **никогда** не принимается из тела запроса.

### Второй рубеж (когда дорастёте): Postgres RLS

```sql
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON documents
    USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

Приложение в начале транзакции выполняет `SET app.current_tenant = '...'`. Даже
SQL-инъекция или забытый фильтр не пробьют изоляцию — defense in depth. На MVP не
обязательно, но `tenant_id` уже везде, добавить policy позже бесплатно.

### Чеклист изоляции

- [ ] `tenant_id NOT NULL` в **каждой** прикладной таблице, включая `document_chunks`
- [ ] `tenant_id` извлекается только из подписанного JWT, никогда из тела/параметра запроса
- [ ] `@Filter` включён на каждый запрос; `TenantContext.clear()` в `finally`
- [ ] **Ни одного** векторного запроса без `WHERE tenant_id`
- [ ] `UNIQUE (tenant_id, email)`, а не глобальный unique по email
- [ ] При записи `tenant_id` ставится сервером (`@PrePersist`), не приходит от клиента
- [ ] Тест: пользователь А запрашивает document_id компании Б → `404`, а не `403`
      (не палим существование чужого ресурса)

---

## 2. Векторный поиск и over-filtering в HNSW

### Почему фильтр ломает HNSW

HNSW — это **граф**. Поиск идёт жадным обходом по геометрической близости векторов;
ширина фронта = `hnsw.ef_search` (по умолчанию 40). **Индекс не знает про `tenant_id`** —
фильтр `WHERE tenant_id = 'A'` применяется **после** того, как индекс вернул кандидатов.

```
Запрос: top-10 для tenant A,  ef_search = 40
индекс возвращает 40 ближайших чанков ВСЕХ тенантов вперемешку
если tenant A = 0.1% данных → почти все 40 чужие → после фильтра 0 строк
→ «ничего не найдено», хотя документы тенанта есть
```

Это **over-filtering**. Чем меньше доля тенанта, тем выше шанс, что весь фронт состоит
из чужих векторов. Recall падает **молча**, без ошибок.

```
Доля тенанта в таблице   Вероятность недобрать top-10 при ef_search=40
─────────────────────────────────────────────────────────────────────
50%   (2 тенанта)        ≈ 0
5%    (20 тенантов)      низкая
0.5%  (200 тенантов)     высокая
0.05% (2000 тенантов)    почти всегда пусто
```

### Запрос — всегда с tenant_id

```sql
SELECT chunk_text, clause_ref
FROM document_chunks
WHERE tenant_id = :tenantId           -- НИКОГДА не опускать
ORDER BY embedding <=> :queryVector   -- косинусное расстояние
LIMIT 10;
```

**Правило: в проекте не должно быть ни одного векторного запроса без `tenant_id` в `WHERE`.**
Вынести в единственный репозиторий-метод, чтобы иначе написать было физически нельзя.

### Решение 1 — `ef_search` побольше (аварийный костыль)

```sql
SET hnsw.ef_search = 1000;
```

Латентность растёт линейно, гарантий нет, для доли 0.01% не хватит и 1000. Лечение симптома.

### Решение 2 — Iterative index scans (pgvector ≥ 0.8.0) — софтовый ответ

Скан **продолжает тянуть** из индекса, пока не наберётся `LIMIT` после фильтрации.

```sql
SET hnsw.iterative_scan = relaxed_order;   -- или strict_order
SET hnsw.max_scan_tuples = 20000;          -- предохранитель от скана всей таблицы
SET hnsw.scan_mem_multiplier = 2;
```

| Режим | Что гарантирует | Когда |
|---|---|---|
| `strict_order` | Строгий порядок по расстоянию | Когда важен точный ранкинг |
| `relaxed_order` | Чуть не по порядку, но быстрее и **лучше recall** | Обычно для RAG |

`max_scan_tuples` — предохранитель: если совпадений у тенанта нет, скан не уйдёт по
всему индексу. Проверьте версию: `SELECT extversion FROM pg_extension WHERE extname='vector';`

### Решение 3 — Partitioning по tenant_id (структурный ответ)

Физически разделяем данные → фильтровать нечего, у каждой секции **свой** HNSW-индекс.

```sql
CREATE TABLE document_chunks (
    id          UUID,
    tenant_id   UUID NOT NULL,
    document_id UUID NOT NULL,
    chunk_text  TEXT,
    embedding   VECTOR(1536),
    PRIMARY KEY (tenant_id, id)
) PARTITION BY HASH (tenant_id);

CREATE TABLE document_chunks_p0 PARTITION OF document_chunks
    FOR VALUES WITH (MODULUS 16, REMAINDER 0);
-- ... p1..p15
CREATE INDEX ON document_chunks_p0 USING hnsw (embedding vector_cosine_ops);
-- ... на каждой секции
```

При `WHERE tenant_id = 'A'` планировщик делает **partition pruning** — лезет в одну секцию.

**Подвох — число секций.** Тысячи партиций убивают планировщик, тысячи HNSW-индексов
жрут память. Стратегия по профилю тенантов:

| Профиль | Стратегия |
|---|---|
| Десятки крупных тенантов | `PARTITION BY LIST (tenant_id)` — секция на тенанта |
| Тысячи мелких + пара китов | Гибрид: LIST на китов + HASH-группа на хвост |
| Много однородных | `PARTITION BY HASH`, 16–64 секции |

### Решение 4 — Partial indexes (только для немногих крупных)

```sql
CREATE INDEX idx_chunks_acme ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WHERE tenant_id = 'acme-uuid';
```

Over-filtering невозможен в принципе, но **не масштабируется на тысячи тенантов**.

### Что выбрать

```
MVP / десятки тенантов:
   Одна таблица + один HNSW + iterative_scan=relaxed_order (pgvector ≥ 0.8) + max_scan_tuples

Рост / сотни–тысячи:
   PARTITION BY HASH, 16–64 секции + HNSW на каждой + iterative_scan включён

Enterprise / киты:
   Гибрид LIST(киты)+HASH(хвост) + partial index точечно
```

`iterative_scan` и партиционирование **не взаимоисключающи** — это разные уровни.
Партиционирование уменьшает долю чужих структурно, итеративный скан добивает остаток.

### Как поймать до прода

1. Нагрузочный тест с **реалистичным перекосом** тенантов (не «1 тенант × 100k»).
2. `recall@k` против полного перебора (см. §4).
3. `EXPLAIN ANALYZE` → большой `Rows Removed by Filter` = индекс гоняет чужие строки вхолостую.

---

## 3. Порядок результатов: `relaxed_order` vs `strict_order`

### Контринтуитивный факт: `relaxed_order` даёт recall *лучше*

| Режим | Порядок по расстоянию | Recall |
|---|---|---|
| `strict_order` | Точный, гарантированный | Чуть **хуже** |
| `relaxed_order` | Может быть нарушен | **Лучше** |

### Механика

Итеративный скан тянет результаты порциями; граф может найти точку 0.30 раньше, чем 0.22.

```
Итерация 1:  [0.10, 0.18, 0.30, 0.41]
Итерация 2:  [0.22, 0.35, 0.55]      ← 0.22 ближе, но пришла позже
```

- **`strict_order`** обязан соблюсти порядок → вынужден **отбрасывать** «опоздавшие» близкие
  результаты → recall ниже.
- **`relaxed_order`** ничего не выбрасывает → recall выше, цена — перемешанный порядок внутри top-k.

### Когда нарушенный порядок вредит RAG

**Безопасно (порядок не важен):**
- Все top-k уходят в LLM целиком, без обрезки по расстоянию.
- Есть реранкер после ретрива (он всё пересортирует — см. §5).

**Вредит (порядок важен):**
- Обрезка контекста по позиции (достали 10, в промпт берём 5) — релевантный чанк выпадает.
- Порог по расстоянию / показ скора пользователю.
- Берёте только #1 (напр. «самый похожий пункт политики» в Compliance Checker).
- «Lost in the middle» — LLM сильнее весит начало/конец контекста.

### Киллер-митигация: `relaxed_order` + досортировка top-k в приложении

`relaxed_order` гарантирует правильное **множество** k чанков (дорого — обход графа).
Упорядочить k=10 строк по уже посчитанному расстоянию — **тривиально**.

```sql
SELECT id, chunk_text, embedding <=> :q AS distance
FROM document_chunks
WHERE tenant_id = :tenantId
ORDER BY embedding <=> :q
LIMIT 10;
```

```java
results.sort(Comparator.comparingDouble(Chunk::distance));  // k=10, копейки
```

Досортировку надёжнее делать в коде над вернувшимися k строками — расстояние уже в руках,
планировщик не вмешается. Получаем recall relaxed-режима + точный порядок strict-режима.

### Решение

```
Есть реранкер?
   └─ Да  → relaxed_order, порядок не трогаем
   └─ Нет:
        Обрезка контекста / нужен скор / берём #1?
           └─ Нет → relaxed_order, и так ок
           └─ Да  → relaxed_order + ДОСОРТИРОВКА top-k в приложении ← дефолт
```

---

## 4. Тестовая стратегия: leak-тесты и recall@k

Развести **две разные вещи**:

| Тест | Что проверяет | Тип | Толерантность |
|---|---|---|---|
| **Leak-тест** | Не просочились ли чужие тенанты | Бинарный (корректность) | **Ноль** |
| **Recall-тест** | Не теряем ли свои (over-filtering) | Метрика (качество) | Порог, напр. ≥ 0.95 |

Утечка тенанта — **не** «низкий recall», это дыра в безопасности. Разные тесты, разные реакции в CI.

### 4a. Leak-тест (изоляция) — бинарный, zero-tolerance

```java
@Test
void semanticSearch_neverReturnsForeignTenant() {
    for (var q : sampleQueries) {
        var results = search.run(q.vector(), tenantA, /*k=*/20);
        assertThat(results)
            .allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(tenantA));
    }
}
```

**Honeypot:** завести тенанта B с документами, **семантически почти идентичными** запросам A.
Если фильтрация отвалится, B всплывёт именно потому, что геометрически ближайший. Random-датасет
такую дыру может не вскрыть (чужие просто далеко).

### 4b. Recall@k-тест (over-filtering) — метрика против ground truth

```
recall@k = |approx_top_k ∩ true_top_k| / k
true_top_k — точный перебор (тот же фильтр тенанта)
Итог усредняем по набору запросов
```

**Ground truth — форсируем точный перебор:**

```sql
BEGIN;
SET LOCAL enable_indexscan = off;
SET LOCAL enable_bitmapscan = off;
SELECT id FROM document_chunks
WHERE tenant_id = :tenantId
ORDER BY embedding <=> :q
LIMIT :k;
COMMIT;
```

> ⚠️ Проверьте `EXPLAIN`, что план — **Seq Scan**. Если индекс подхватился, ground truth неточный.

```java
double recallAtK(Set<UUID> truth, List<UUID> approx, int k) {
    long hit = approx.stream().limit(k).filter(truth::contains).count();
    return (double) hit / k;
}
```

**Подводные камни:**

1. **Агрегат скрывает обвал.** Средний recall 0.98 прячет обвал у мелкого тенанта (0.2).
   Считать **в разбивке по размеру тенанта**:

   ```
   Бакет тенанта      recall@10
   крупный (>50k)       0.99
   средний (1k–50k)     0.97
   мелкий (<500)        0.61   ← over-filtering, средний это прятал
   ```

2. **Ничьи (ties).** При дублях сравнивать по расстоянию k-го элемента, а не строго по id.
3. **Тот же вектор, та же метрика** (`<=>` cos vs `<->` L2) в truth и approx.
4. **Реалистичный перекошенный датасет**, а не «1 тенант × 100k».
5. **Стабильность для CI:** фиксировать сид/датасет, порог с запасом (см. §6 про стохастичность).

### Сборка в регрессионный набор

```
tests/
  isolation/
    LeakTest               → чужой tenant_id = FAIL (zero tolerance, блокирует мёрж)
    HoneypotLeakTest       → тенант-двойник рядом в векторном пространстве
  recall/
    RecallByTenantSizeTest → recall@k по бакетам; FAIL если мелкие < порога
```

---

## 5. Реранкер

### Bi-encoder vs cross-encoder

- **Векторный поиск = bi-encoder:** запрос и документ кодируются по отдельности, близость = косинус.
  Быстро, но грубо (смысл сжат в вектор до того, как модель увидела запрос).
- **Реранкер = cross-encoder:** пара (запрос + чанк) прогоняется через трансформер вместе.
  Точнее, но нельзя предпосчитать.

```
Запрос
  ├─ Этап 1: bi-encoder (pgvector)   → top-N (N=50–100), дёшево
  └─ Этап 2: cross-encoder (реранкер) → top-k (k=5–10), дорого, точно
```

### Почему для контрактов ценность выше среднего

- **Критические различия в почти идентичных формулировках:** «несёт» vs «не несёт»
  ответственность — почти одинаковые эмбеддинги, противоположный смысл. Косинус не различит,
  cross-encoder — да.
- **Шкалы/пороги:** «0.1% в день» vs «0.1% за весь срок».
- **Compliance Checker критично зависит от precision** — ложный пункт даёт ложный вывод.

Для Compliance реранкер почти обязателен; для простого Semantic Search можно без него на MVP.

### Три бонуса

1. **Снимает вопрос порядка из §3** — cross-encoder пересортировывает всё, порядок pgvector затирается.
   `relaxed_order` можно включать смело, про досортировку забыть.
2. **Повышает устойчивость к over-filtering** — over-fetch N=50–100 внутри тенанта; чем больше k
   у HNSW, тем меньше бьёт over-filtering.
3. **Изоляцию не усложняет** — реранкер stateless, работает над уже отфильтрованными кандидатами.

### Реализация

| Вариант | Тип | Замечание |
|---|---|---|
| Cohere Rerank | Hosted | Контракты уходят во внешний сервис (вопрос приватности) |
| **bge-reranker-v2-m3** | Open, self-host | Мультиязычный (важно для русских договоров) |
| Jina Reranker | Hosted/open | Альтернатива |

**Рекомендация:** этап 1 (pgvector, `relaxed_order`, over-fetch N=50) → этап 2 (bge-reranker
self-host) → top-10 в LLM. Для приватных контрактов — self-host, не hosted API.

---

## 6. Построение HNSW: `m`, `ef_construction`, стохастичность

Не путать с `ef_search` (query-time). Качество графа задаётся при построении:

```sql
CREATE INDEX ON document_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);   -- дефолты pgvector
```

### `m` — число связей на узел

| ↑ `m` | Эффект |
|---|---|
| Recall | Лучше |
| Память | Больше (≈ пропорционально `m`) |
| Скорость поиска/стройки | Медленнее |

Дефолт 16; для dim=1536 и требований к recall — **32**. Выше 64 редко оправдано.

### `ef_construction` — ширина фронта при стройке

| ↑ `ef_construction` | Эффект |
|---|---|
| Качество графа / recall | Лучше |
| Скорость стройки | Медленнее (главная цена) |
| Память / скорость поиска | Не влияет (build-time only) |

Дефолт 64; поднять до **128–200** — заметно улучшает recall, в рантайме «бесплатно».

### Связь с over-filtering

`m` и `ef_construction` поднимают **базовый** recall → больше запас до того, как тенант-фильтрация
его проест. Для мульти-тенантной системы с перекосом строить качественнее дефолта
(`m=32, ef_construction=128`) — даёт over-filtering-защите фору ещё до `iterative_scan`.

### Стохастичность — ломает recall-тесты из §4

Построение HNSW **недетерминировано**: граф зависит от порядка вставки и (при параллельной
стройке) расписания потоков. Один датасет дважды → разные графы → разный `recall@k`.

1. **Порог не впритык.** Recall плавает 0.95 ± 0.02 → порог `≥ 0.95` будет флапать. Ставить с запасом.
2. **Фиксировать фикстуру и порядок вставки** (стабильный сид).
3. **Мерять на нескольких прогонах** для калибровки порога (порог = минимум разброса − запас).

### Стратегия построения

- **Стройте индекс ПОСЛЕ массовой загрузки**, не до (bulk-load → `CREATE INDEX`).
- Поднимите **`maintenance_work_mem`** на время стройки.
- Параллельная стройка ускоряет, но усиливает стохастичность.
- С партиционированием индекс строится на каждой секции отдельно — плюс (быстрее, параллельно,
  нового кита индексируем не трогая остальных).

---

## 7. Итоговые рекомендации

```
Multi-tenancy:  Shared DB + tenant_id + Hibernate @Filter; tenant_id из JWT;
                TenantContext.clear() в finally; @PrePersist на запись; RLS позже
Индекс:         m = 32, ef_construction = 128   (запас recall под мульти-тенантность)
Поиск:          iterative_scan = relaxed_order, ef_search ~40, max_scan_tuples лимит
                + досортировка top-k в приложении (если нет реранкера)
Стройка:        после bulk-load, высокий maintenance_work_mem, на каждой секции
Партиционирование: при сотнях–тысячах тенантов — PARTITION BY HASH 16–64 секции
Реранкер:       bge-reranker self-host вторым этапом (особенно Compliance) —
                снимает вопрос порядка, смягчает over-filtering, изоляцию не усложняет
Тесты:          LeakTest (zero-tolerance, блокирует мёрж) + RecallByTenantSizeTest
                (порог с запасом, разбивка по размеру тенанта, фиксированная фикстура)
```

### Связь компонентов

1. `relaxed_order` ради recall → recall@k-тест подтверждает, что over-filtering на мелких тенантах ушёл.
2. Досортировка top-k (или реранкер) → точный порядок, recall не пострадал.
3. Leak-тест сторожит, что возня с `ef_search`/итеративным сканом не пробила изоляцию.
4. `m`/`ef_construction` дают базовый запас recall; стохастичность стройки требует порогов с запасом.

recall@k — не абстрактная метрика, а **датчик** регресса тех ручек, что крутили выше.
