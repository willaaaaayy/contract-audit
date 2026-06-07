package com.contractaudit.document.processing;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * OCR-извлечение текста из сканов: каждая страница PDF рендерится в изображение (PDFBox)
 * и распознаётся Tesseract (Tess4J). См. docs/retrieval-design.md (пайплайн PDF).
 *
 * <p>Нативная {@code libtesseract} грузится JNA лениво — при первом {@code doOCR}, а не при
 * создании бина. Поэтому приложение стартует даже там, где Tesseract не установлен; падение
 * случится только при реальной попытке распознать скан (и будет помечено как FAILED).
 */
@Component
public class TesseractOcrExtractor implements TextExtractor {

    private final OcrProperties properties;

    public TesseractOcrExtractor(OcrProperties properties) {
        this.properties = properties;
        // Помогаем JNA найти нативную библиотеку (напр. /opt/homebrew/lib на macOS).
        if (properties.libraryPath() != null && !properties.libraryPath().isBlank()) {
            String existing = System.getProperty("jna.library.path", "");
            String merged = existing.isBlank()
                    ? properties.libraryPath()
                    : existing + File.pathSeparator + properties.libraryPath();
            System.setProperty("jna.library.path", merged);
        }
    }

    @Override
    public String extract(byte[] content, String filename) {
        ITesseract tesseract = new Tesseract();
        if (properties.datapath() != null && !properties.datapath().isBlank()) {
            tesseract.setDatapath(properties.datapath());
        }
        tesseract.setLanguage(properties.language());

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, properties.dpi(), ImageType.GRAY);
                text.append(tesseract.doOCR(image)).append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось отрендерить PDF '%s' для OCR".formatted(filename), e);
        } catch (TesseractException e) {
            throw new RuntimeException("OCR не удался для '%s'".formatted(filename), e);
        }
    }
}
