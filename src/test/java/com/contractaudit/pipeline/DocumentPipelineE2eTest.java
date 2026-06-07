package com.contractaudit.pipeline;

import com.contractaudit.chunk.ChunkMatch;
import com.contractaudit.document.DocumentService;
import com.contractaudit.document.DocumentStatus;
import com.contractaudit.document.processing.DocumentProcessingService;
import com.contractaudit.retrieval.SemanticSearchService;
import com.contractaudit.support.AbstractPgvectorTest;
import com.contractaudit.support.StubEmbeddingConfig;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Сквозной тест пайплайна без OpenAI (стаб-эмбеддер): доказывает всю цепочку
 * {@code register → @Async обработка (PDFBox → chunk → embed → store) → semantic search}
 * на реальном pgvector.
 *
 * <p>Идём на уровне сервисов (а не HTTP) — контроллер {@code DocumentController} лишь тонко
 * оборачивает эти же вызовы. Изоляция сохраняется и через async-границу: {@code tenantId}
 * передаётся в воркер явно.
 *
 * <p>{@code chunk-max-chars=70} специально мал, чтобы короткий договор разбился на несколько
 * чанков и поиск реально различал релевантный пункт, а не возвращал единственный чанк.
 */
@Import(StubEmbeddingConfig.class)
@TestPropertySource(properties = "processing.chunk-max-chars=70")
class DocumentPipelineE2eTest extends AbstractPgvectorTest {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private DocumentProcessingService processingService;
    @Autowired
    private SemanticSearchService searchService;

    @Test
    void uploadProcessSearch_findsRelevantClause_isolatedPerTenant() throws Exception {
        UUID tenant = createTenant("acme");

        byte[] pdf = makePdf(List.of(
                "7.1 The Supplier warrants the quality of all delivered goods.",
                "7.2 The Buyer pays for delivery within five business days.",
                "8.1 Either party may terminate this agreement upon written notice.",
                "9.1 Both parties keep the commercial terms strictly confidential."));

        // Регистрация коммитит документ (PENDING) + blob; обработка читает байты из blob.
        UUID documentId = withTenant(tenant,
                () -> documentService.register("contract.pdf", null, pdf).getId());

        // Нудж обработки (tenantId протаскиваем явно — ThreadLocal не перетекает на воркер).
        processingService.process(tenant, documentId);
        awaitStatus(tenant, documentId, DocumentStatus.DONE, Duration.ofSeconds(20));

        // Семантический поиск по смыслу запроса о доставке.
        List<ChunkMatch> hits = withTenant(tenant, () -> searchService.search("who pays for delivery"));

        assertThat(hits).as("поиск должен что-то найти после обработки").isNotEmpty();
        assertThat(hits)
                .as("все результаты принадлежат документу этого арендатора")
                .allSatisfy(hit -> assertThat(hit.documentId()).isEqualTo(documentId));
        assertThat(hits.get(0).chunkText().toLowerCase())
                .as("самый релевантный чанк — про оплату доставки")
                .contains("delivery");
    }

    private void awaitStatus(UUID tenant, UUID documentId, DocumentStatus expected, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            DocumentStatus status = withTenant(tenant,
                    () -> documentService.get(documentId).orElseThrow().getStatus());
            if (status == expected) {
                return;
            }
            if (status == DocumentStatus.FAILED) {
                throw new AssertionError("Обработка документа завершилась со статусом FAILED");
            }
            sleep();
        }
        throw new AssertionError("Документ не достиг статуса " + expected + " за " + timeout);
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** Простой текстовый PDF из строк (Helvetica, ASCII) для проверки реального извлечения. */
    private static byte[] makePdf(List<String> lines) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(20f);
                cs.newLineAtOffset(50, 760);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                    cs.newLine();   // пустая строка → разделитель абзацев при извлечении
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
