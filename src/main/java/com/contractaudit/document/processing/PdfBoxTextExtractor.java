package com.contractaudit.document.processing;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Извлечение текста из цифровых (текстовых) PDF через Apache PDFBox.
 *
 * <p>Сканы (картинки без текстового слоя) PDFBox отдать не может — для них извлечённый
 * текст почти пуст. Эвристика ниже распознаёт такой случай и сигнализирует
 * {@link OcrRequiredException}, не пытаясь молча обработать пустоту. Сам OCR (Tesseract
 * или облачный) — отдельный маршрут, который подключим позже.
 */
@Component
public class PdfBoxTextExtractor implements TextExtractor {

    /** Меньше этого числа «значимых» символов на страницу → считаем документ сканом. */
    private static final int MIN_CHARS_PER_PAGE = 40;

    @Override
    public String extract(byte[] content, String filename) {
        try (PDDocument document = Loader.loadPDF(content)) {
            String text = new PDFTextStripper().getText(document);
            int pages = Math.max(1, document.getNumberOfPages());

            if (text.strip().length() < (long) MIN_CHARS_PER_PAGE * pages) {
                throw new OcrRequiredException(
                        "Документ '%s' выглядит как скан (мало текста на %d стр.) — требуется OCR"
                                .formatted(filename, pages));
            }
            return text;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать PDF '%s'".formatted(filename), e);
        }
    }
}
