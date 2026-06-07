package com.contractaudit.document.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Основной {@link TextExtractor}, в который ходит пайплайн: пробует цифровое извлечение
 * ({@link PdfBoxTextExtractor}), а если документ оказался сканом
 * ({@link OcrRequiredException}) — переключается на OCR ({@link TesseractOcrExtractor}).
 *
 * <p>{@code @Primary}, поэтому {@link DocumentProcessingService} получает именно его, а не
 * конкретные экстракторы. Если OCR выключен ({@code ocr.enabled=false}) — скан остаётся
 * необработанным, исключение пробрасывается и документ помечается FAILED.
 */
@Component
@Primary
public class CompositeTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(CompositeTextExtractor.class);

    private final PdfBoxTextExtractor digital;
    private final TesseractOcrExtractor ocr;
    private final boolean ocrEnabled;

    public CompositeTextExtractor(PdfBoxTextExtractor digital,
                                  TesseractOcrExtractor ocr,
                                  OcrProperties ocrProperties) {
        this.digital = digital;
        this.ocr = ocr;
        this.ocrEnabled = ocrProperties.enabled();
    }

    @Override
    public String extract(byte[] content, String filename) {
        try {
            return digital.extract(content, filename);
        } catch (OcrRequiredException e) {
            if (!ocrEnabled) {
                log.warn("Документ '{}' — скан, но OCR выключен", filename);
                throw e;
            }
            log.info("Документ '{}' — скан, переключаюсь на OCR", filename);
            return ocr.extract(content, filename);
        }
    }
}
