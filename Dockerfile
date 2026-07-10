# --- Сборка ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Сначала только pom + wrapper — слой зависимостей кэшируется отдельно от кода
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# --- Рантайм ---
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

# Tesseract для OCR сканов (см. CompositeTextExtractor), eng+rus языки.
# curl — для HEALTHCHECK ниже.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-rus curl \
    && rm -rf /var/lib/apt/lists/*

# Подсказки приложению, где нативная libtesseract и tessdata. Каталог нативных библиотек
# зависит от архитектуры (x86_64 vs aarch64) — прячем разницу за симлинком /usr/lib/native.
ARG TARGETARCH
RUN ln -s "/usr/lib/$([ "$TARGETARCH" = "arm64" ] && echo aarch64 || echo x86_64)-linux-gnu" /usr/lib/native
ENV OCR_LIBRARY_PATH=/usr/lib/native \
    OCR_TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata

COPY --from=build /app/target/contract-audit-*.jar app.jar

# Не root: в ubuntu-noble уже есть пользователь ubuntu (UID/GID 1000) — переиспользуем его;
# UID согласован с runAsUser в k8s/deployment.yaml. Пишем только в /tmp (world-writable).
USER 1000:1000

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
