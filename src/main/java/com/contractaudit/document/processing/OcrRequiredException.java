package com.contractaudit.document.processing;

/**
 * Документ выглядит как скан (картинка без текстового слоя) — цифровое извлечение
 * бесполезно, нужен OCR. Отдельный тип, чтобы пайплайн мог развести цифровой и
 * OCR-маршруты, когда OCR появится. См. docs/retrieval-design.md (пайплайн PDF).
 */
public class OcrRequiredException extends RuntimeException {

    public OcrRequiredException(String message) {
        super(message);
    }
}
