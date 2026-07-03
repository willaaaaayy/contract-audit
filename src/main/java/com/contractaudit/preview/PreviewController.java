package com.contractaudit.preview;

import com.contractaudit.common.upload.UploadValidator;
import com.contractaudit.document.processing.ContractChunker;
import com.contractaudit.document.processing.TextChunk;
import com.contractaudit.document.processing.TextExtractor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Быстрое превью загруженного PDF — синхронно, без OpenAI и без записи в БД: извлекает текст
 * (PDFBox/OCR), разбивает по пунктам и выделяет даты/суммы. Питает страницу загрузки в браузере.
 *
 * <p>Открытый эндпоинт (демо). Полный анализ (риски/комплаенс/поиск) идёт через {@code /api/documents}
 * и требует аутентификации + OpenAI-ключа.
 */
@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    private final TextExtractor textExtractor;
    private final ContractChunker chunker;
    private final KeyDataExtractor keyDataExtractor;
    private final UploadValidator uploadValidator;

    public PreviewController(TextExtractor textExtractor,
                            ContractChunker chunker,
                            KeyDataExtractor keyDataExtractor,
                            UploadValidator uploadValidator) {
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.keyDataExtractor = keyDataExtractor;
        this.uploadValidator = uploadValidator;
    }

    @PostMapping
    public PreviewResponse preview(@RequestParam("file") MultipartFile file) throws IOException {
        uploadValidator.validatePdf(file);
        String filename = file.getOriginalFilename();
        String text = textExtractor.extract(file.getBytes(), filename);

        List<PreviewResponse.Clause> clauses = chunker.chunk(text).stream()
                .map(PreviewController::toClause)
                .toList();
        KeyDataExtractor.KeyData keyData = keyDataExtractor.extract(text);

        return new PreviewResponse(filename, text.length(), clauses.size(), text, clauses,
                keyData.dates(), keyData.amounts());
    }

    private static PreviewResponse.Clause toClause(TextChunk chunk) {
        return new PreviewResponse.Clause(chunk.index(), chunk.clauseRef(), chunk.text());
    }

    /**
     * @param filename   имя файла
     * @param characters длина извлечённого текста
     * @param clauseCount число фрагментов/пунктов
     * @param fullText   весь извлечённый текст
     * @param clauses    фрагменты с ссылками на пункты
     * @param dates      найденные даты
     * @param amounts    найденные суммы
     */
    public record PreviewResponse(String filename, int characters, int clauseCount, String fullText,
                                  List<Clause> clauses, List<String> dates, List<String> amounts) {

        public record Clause(int index, String clauseRef, String text) {
        }
    }
}
