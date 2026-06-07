# --- Сборка ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Сначала только pom + wrapper — слой зависимостей кэшируется отдельно от кода
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

# --- Рантайм ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Tesseract для OCR сканов (см. CompositeTextExtractor). eng+rus языки.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-rus \
    && rm -rf /var/lib/apt/lists/*

# Подсказки приложению, где нативная libtesseract и tessdata в Debian/Ubuntu
ENV OCR_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu \
    OCR_TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata

COPY --from=build /app/target/contract-audit-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
