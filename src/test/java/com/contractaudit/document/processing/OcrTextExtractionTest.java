package com.contractaudit.document.processing;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Реальный OCR-маршрут: image-only PDF (без текстового слоя) распознаётся, когда цифровое
 * извлечение пасует. Требует установленного Tesseract; иначе тест пропускается (assume).
 */
class OcrTextExtractionTest {

    private static final String LIBRARY_PATH = env("OCR_LIBRARY_PATH", "/opt/homebrew/lib");
    private static final String TESSDATA_PATH = env("OCR_TESSDATA_PATH", "/opt/homebrew/share/tessdata");

    @BeforeAll
    static void requireTesseract() {
        assumeTrue(new File(TESSDATA_PATH, "eng.traineddata").exists(),
                "нет eng.traineddata — пропускаю OCR-тест");
        assumeTrue(hasNativeLib(LIBRARY_PATH), "нет нативной libtesseract — пропускаю OCR-тест");
    }

    @Test
    void scannedPdfIsRoutedToOcrAndTextRecognized() throws Exception {
        OcrProperties properties = new OcrProperties(true, "eng", 300, TESSDATA_PATH, LIBRARY_PATH);
        TextExtractor extractor = new CompositeTextExtractor(
                new PdfBoxTextExtractor(), new TesseractOcrExtractor(properties), properties);

        byte[] scannedPdf = imageOnlyPdf("DELIVERY PAYMENT WITHIN FIVE DAYS");

        // Цифровое извлечение пасует (текстового слоя нет) → композит уходит в OCR.
        String text = extractor.extract(scannedPdf, "scan.pdf");

        assertThat(text.toUpperCase()).contains("DELIVERY");
    }

    /** PDF из одной картинки с текстом — без текстового слоя, как настоящий скан. */
    private static byte[] imageOnlyPdf(String message) throws Exception {
        BufferedImage image = new BufferedImage(1400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 64));
        g.drawString(message, 30, 180);
        g.dispose();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
            document.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static boolean hasNativeLib(String dir) {
        File d = new File(dir);
        String[] names = d.list();
        if (names == null) {
            return false;
        }
        for (String n : names) {
            if (n.startsWith("libtesseract") && (n.endsWith(".dylib") || n.endsWith(".so"))) {
                return true;
            }
        }
        return false;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
