package com.contractaudit.document.processing;

/**
 * Извлекает текст из загруженного документа. Точка расширения для разных типов входа:
 * цифровые PDF ({@link PdfBoxTextExtractor}) и — в будущем — сканы через OCR.
 */
public interface TextExtractor {

    /**
     * @param content байты файла
     * @param filename имя файла (для логов/диагностики)
     * @return извлечённый текст
     * @throws OcrRequiredException если это, похоже, скан и нужен OCR-маршрут
     */
    String extract(byte[] content, String filename);
}
