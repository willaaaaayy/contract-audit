package com.contractaudit.document.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры OCR-маршрута для сканов.
 *
 * @param enabled     включён ли OCR (если нет — скан помечается FAILED)
 * @param language    языки Tesseract (напр. {@code eng}, {@code rus+eng})
 * @param dpi         разрешение рендеринга страниц PDF в изображение перед OCR
 * @param datapath    путь к tessdata (если пусто — Tesseract берёт TESSDATA_PREFIX)
 * @param libraryPath доп. путь для JNA к нативной libtesseract (напр. {@code /opt/homebrew/lib})
 */
@ConfigurationProperties(prefix = "ocr")
public record OcrProperties(boolean enabled, String language, int dpi, String datapath, String libraryPath) {

    public OcrProperties {
        if (language == null || language.isBlank()) {
            language = "eng";
        }
        if (dpi <= 0) {
            dpi = 300;
        }
    }
}
