# Contract Audit — AI-ассистент для анализа и аудита B2B-контрактов

Сервис, в который юридические отделы и отделы закупок загружают PDF-договоры, а ИИ
находит риски, скрытые условия и сверяет их с внутренними политиками компании.

## Стек

- **Backend:** Java 21, Spring Boot 3.4
- **AI:** Spring AI — OpenAI **или** локальный Ollama (chat + embeddings) по профилю; cross-encoder реранкер (TEI, опционально)
- **Security:** Spring Security + собственная выдача JWT (RS256), мультиарендность, rate limiting
- **БД:** PostgreSQL + pgvector (метаданные + семантический поиск)
- **Хранилище blob:** Postgres (по умолчанию) или S3/MinIO
- **OCR:** Tesseract (Tess4J) для сканов
- **Observability:** Actuator + Micrometer/Prometheus
- **Миграции:** Flyway

## Архитектура

Решения по слою хранения и поиска — в [`docs/retrieval-design.md`](docs/retrieval-design.md):
multi-tenancy, over-filtering в HNSW, `relaxed_order`/recall@k, реранкер, построение HNSW.

Ключевые инварианты:
- **Изоляция арендаторов** — `tenant_id` в каждой таблице. Метаданные защищает нативная
  мультиарендность Hibernate (`@TenantId`); векторный поиск — единственный репозиторий
  `DocumentChunkRepository`, где `WHERE tenant_id` зашит и не может быть опущен.
- **Источник арендатора** — только claim подписанного JWT, никогда не из тела запроса.

## Локальный запуск

```bash
# 1. Поднять Postgres + pgvector
docker compose up -d

# 2. Переменные окружения
export OPENAI_API_KEY=sk-...
export JWT_ISSUER_URI=http://localhost:9000   # ваш IdP (Keycloak/Auth0/...)

# 3. Запустить приложение (Flyway применит миграции на старте)
./mvnw spring-boot:run
```

## Локальный inference (self-hosted / VDS) — без облачных ключей

Профиль `local` поднимает весь AI на **Ollama** (chat + embeddings), так что сервис
работает на своём VDS без OpenAI. Реранкер не обязателен — без TEI используется фолбэк
distance-sort.

```bash
# 1. Поднять инфраструктуру + Ollama и скачать модели (init-сервис тянет их и завершается)
docker compose up -d
docker compose logs -f ollama-init    # дождаться «Модели готовы.»

# 2. Запустить приложение в профиле local (OPENAI_API_KEY не нужен)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
#   либо: SPRING_PROFILES_ACTIVE=local java -jar target/contract-audit-*.jar
```

Модели по умолчанию (переопределяются env, см. ниже):

| Роль       | Модель                | Размерность | Заметка                                   |
|------------|-----------------------|-------------|-------------------------------------------|
| chat       | `qwen2.5:7b-instruct` | —           | сильный русский; для слабого VDS → `:3b`  |
| embeddings | `bge-m3`              | 1024        | мультиязычный, отлично для русского        |

**Размерность.** Схема — `VECTOR(1536)` (под OpenAI). `bge-m3` даёт 1024, поэтому
`PaddingEmbeddingModel` дополняет вектор нулями до 1536 — косинусное расстояние при этом
сохраняется в точности, миграции и HNSW-индекс не трогаем. Если хотите нативные 1024 —
поменяйте `VECTOR(1536)` в миграциях на чистой БД и задайте `EMBEDDING_TARGET_DIM=1024`.

Переменные окружения профиля `local`:

```
OLLAMA_BASE_URL      — адрес Ollama (по умолчанию http://localhost:11434)
OLLAMA_CHAT_MODEL    — чат-модель (по умолчанию qwen2.5:7b-instruct)
OLLAMA_EMBED_MODEL   — эмбеддер (по умолчанию bge-m3)
EMBEDDING_TARGET_DIM — целевая размерность под схему (по умолчанию 1536)
```

Под слабый VDS (CPU, мало RAM): `OLLAMA_CHAT_MODEL=qwen2.5:3b-instruct docker compose up -d`
и тот же оверрайд при запуске приложения. GPU включается раскомментированием блока `deploy`
у сервиса `ollama` (нужен nvidia-container-toolkit). Переключение провайдеров — взаимно
исключающее на одну БД: эмбеддинги разных моделей несравнимы, при смене модели документы
нужно переобработать.

## Тесты

Интеграционные тесты изоляции и recall (`src/test/.../chunk/`) гоняются на реальном
pgvector. Два способа подключить БД:

```bash
# Вариант 1 — Testcontainers (по умолчанию, для CI): сам поднимает контейнер
./mvnw test

# Вариант 2 — внешняя БД (compose уже запущен). Нужен там, где бандленный в
# Testcontainers docker-java несовместим с версией Docker-демона (напр. Docker 29+,
# где минимальная версия API 1.44, а docker-java 3.4.0 шлёт 1.32).
docker compose up -d
EXTERNAL_DB=true ./mvnw test
```

Что проверяется (см. дизайн-док §4):
- **leak / honeypot** — чанк чужого арендатора с вектором, идентичным запросу (расстояние 0),
  не попадает в выдачу; владельцу — попадает;
- **recall@k** — у мелкого арендатора под шумом крупного recall@10 ≥ 0.9 против точного
  перебора (Seq Scan), т.е. `relaxed_order` + iterative scan гасят over-filtering.

## Структура пакетов

```
com.contractaudit
├── auth       — выдача JWT (RSA/RS256): register/login, TokenService, локальный JwtDecoder
├── tenant     — TenantContext, резолвер @TenantId, slug, фильтр извлечения tenant из JWT
├── security   — конфигурация JWT resource server + PasswordEncoder
├── user       — AppUser + репозиторий
├── document   — метаданные документов (Document, статусы)
├── chunk      — document_chunks: нативный pgvector-доступ (изоляция + поиск)
├── retrieval  — двухэтапный семантический поиск (pgvector → реранкер)
├── risk       — Risk Scanner: ChatModel + structured output → document_risks
├── policy     — библиотека политик компании (pgvector)
├── compliance — Compliance Checker: сверка договора с политиками (CONTRADICTION/MISSING_REQUIRED)
├── document
│   └── processing — PDF-пайплайн: TextExtractor (PDFBox + OCR) → ContractChunker → embed → store
└── config     — AsyncConfig (пул фоновой обработки)
```

## Пайплайн обработки документа

`POST /api/documents` (multipart) → регистрация (`PENDING`) → ответ `202` →
фоновая обработка `@Async`:

```
upload → register (PENDING + blob, коммит) → claim → load blob →
   extract → chunk (по пунктам, clause_ref) → embed (батчами) → store → DONE
   │
   ├─ цифровой PDF → PdfBoxTextExtractor
   └─ скан → OcrRequiredException → Tesseract OCR (CompositeTextExtractor)
```

**Долговечность:** байты PDF персистятся (`document_blobs`), обработку открывает атомарный
claim (conditional UPDATE), поэтому документ не обрабатывается дважды. `@Scheduled`-поллер
подбирает `PENDING` и зависшие `PROCESSING` (по таймауту) — переживает рестарт/краш.
Системный запрос поллера идёт мимо `@TenantId` (через всех арендаторов), дальше обработка —
в контексте нужного арендатора.

OCR требует установленного Tesseract; `ocr.library-path` помогает JNA найти нативную
`libtesseract` (macOS: `/opt/homebrew/lib`). Нативная либа грузится лениво — без неё
приложение стартует, цифровые PDF работают, ломается лишь реальная попытка OCR.

Изоляция через async-границу: `tenant_id` берётся на потоке запроса и **явно**
передаётся в воркер (ThreadLocal не перетекает между потоками).

## Статус

```
✅ Каркас (build, схема, multi-tenancy, сущности, retrieval)
✅ REST (документы, поиск, риски) + async PDF-пайплайн (parse → chunk → embed → store)
✅ Risk Scanner — ChatModel + structured output, риски с цитатой и ссылкой на пункт
✅ OCR-маршрут для сканов (Tesseract через Tess4J, фолбэк от цифрового извлечения)
✅ Compliance Checker — сверка с политиками: CONTRADICTION (LLM) + MISSING_REQUIRED
   (обязательная политика без релевантного пункта)
✅ Реальный cross-encoder реранкер (BgeReranker → TEI /rerank), заглушка distance-sort
   как фолбэк, переключение по retrieval.rerank.url
✅ Долговечность пайплайна — персист blob, атомарный claim, @Scheduled-поллер
   (PENDING + зависшие PROCESSING), восстановление после краша
✅ Authn-сервис выдачи JWT — RS256, register/login через workspace-slug, локальная валидация
✅ Прод-харднинг — RSA-ключи из конфигурации (PEM), invite-флоу (ADMIN-only),
   rate limiting auth, Actuator/Micrometer метрики, blob в S3/MinIO
✅ Тесты (36): изоляция (leak/honeypot + recall@k), unit chunker, e2e (upload→process→search),
   Risk Scanner, Compliance, OCR (реальный Tesseract), bge-реранкер, claim/поллер, auth (JWT),
   invite (ADMIN-гейт), rate limit (429), метрики, S3 (MinIO)
```

**Все 4 ключевые функции MVP закрыты:** AI Risk Scanner · Compliance Checker ·
Semantic Search · Multi-tenancy. Прод-харднинг и эксплуатация выполнены.

### Эксплуатация (ops)

```
Dockerfile               — multi-stage сборка, JRE 21, Tesseract для OCR
.github/workflows/ci.yml — CI: тесты на сервисах (postgres/redis/minio) + сборка образа
k8s/                     — Deployment(+probes/HPA), Service, Ingress, ConfigMap,
                           Secret-шаблон, ServiceMonitor, PrometheusRule (алерты)
load/contract-audit.js   — нагрузочный сценарий k6 (ramp, пороги p95/error-rate)
rate-limit.backend=redis — общий лимит для нескольких реплик (Lua token-bucket)
```

### Конфигурация прод-харднинга

```
auth.private-key / auth.public-key    — PEM RSA-ключи (иначе in-memory, только dev)
rate-limit.auth.capacity/refill       — лимит auth-запросов по IP
storage.blob.type = db | s3           — где хранить байты PDF
storage.s3.*                          — endpoint/bucket/ключи (MinIO/S3)
management.endpoints.web.exposure     — health, info, prometheus, metrics
```

## API (сводка)

```
GET  /                              — браузерная демо-страница: загрузка PDF → текст/пункты/даты/суммы
POST /api/preview                   — превью PDF без авторизации и без LLM (extract → chunk → даты/суммы)
POST /api/auth/register             — регистрация компании + admin, выдаёт JWT
POST /api/auth/login                — вход (slug + email + пароль), выдаёт JWT
POST /api/documents                 — загрузка PDF (202, async-обработка)
GET  /api/documents                 — список документов арендатора
GET  /api/documents/{id}            — статус/метаданные
GET  /api/documents/{id}/text       — извлечённый текст (склейка чанков) для DocumentViewer
POST /api/search                    — семантический поиск по смыслу
POST /api/documents/{id}/risks      — запустить Risk Scanner
GET  /api/documents/{id}/risks      — найденные риски
POST /api/policies                  — добавить политику компании
GET  /api/policies                  — список политик
POST /api/documents/{id}/compliance — сверить договор с политиками
GET  /api/documents/{id}/compliance — результаты сверки
POST /api/documents/{id}/profile    — генеративный профиль (тип договора + блоки-чек-листы)
POST /api/documents/{id}/suggestions — предложения по правкам рискованных пунктов (?all=false)
POST /api/users                     — пригласить пользователя (ADMIN-only)
GET  /actuator/health|prometheus    — health и метрики
```
