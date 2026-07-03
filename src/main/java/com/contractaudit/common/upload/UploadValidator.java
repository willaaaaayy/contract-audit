package com.contractaudit.common.upload;

import com.contractaudit.common.error.InvalidUploadException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * Валидация загружаемых договоров до какой-либо обработки: пустой файл, отсутствующее имя
 * и явно не-PDF отбрасываются сразу с 400, не доходя до PDFBox/OCR (публичный
 * {@code /api/preview} делает извлечение синхронно — мусорный ввод дорого стоит).
 * Лимит размера отдельно контролирует multipart-конфигурация (413).
 */
@Component
public class UploadValidator {

    public void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Файл пуст");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidUploadException("Не указано имя файла");
        }
        boolean pdfName = filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean pdfType = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(file.getContentType());
        if (!pdfName && !pdfType) {
            throw new InvalidUploadException("Поддерживаются только PDF-файлы");
        }
    }
}
