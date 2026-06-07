package com.contractaudit.document.processing;

import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.chunk.NewChunk;
import com.contractaudit.document.DocumentService;
import com.contractaudit.document.blob.BlobStorage;
import com.contractaudit.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Фоновый пайплайн обработки документа: {@code claim → load blob → extract → chunk → embed
 * → store}. См. docs/retrieval-design.md (пайплайн PDF).
 *
 * <p><b>Долговечность:</b> байты PDF не передаются в память воркера, а читаются из
 * {@link BlobRepository} (персист в БД). Обработку открывает атомарный
 * {@link ProcessingJobRepository#tryClaim claim}, поэтому один документ не обработается
 * дважды (нудж от загрузки vs поллер vs несколько тиков). Зависший после краша
 * {@code PROCESSING} переклеймливается по таймауту. Переобработка идемпотентна —
 * старые чанки удаляются перед записью новых.
 *
 * <p><b>Изоляция через async-границу:</b> {@link TenantContext} живёт в {@code ThreadLocal}
 * и НЕ перетекает на поток пула, поэтому {@code tenantId} передаётся параметром и
 * выставляется на воркер-потоке (с очисткой в {@code finally}).
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final TextExtractor textExtractor;
    private final ContractChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final BlobStorage blobStorage;
    private final ProcessingJobRepository jobRepository;
    private final DocumentService documentService;
    private final MeterRegistry meterRegistry;
    private final int embedBatchSize;
    private final Duration staleAfter;

    public DocumentProcessingService(TextExtractor textExtractor,
                                     ContractChunker chunker,
                                     EmbeddingModel embeddingModel,
                                     DocumentChunkRepository chunkRepository,
                                     BlobStorage blobStorage,
                                     ProcessingJobRepository jobRepository,
                                     DocumentService documentService,
                                     ProcessingProperties properties,
                                     MeterRegistry meterRegistry) {
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.blobStorage = blobStorage;
        this.jobRepository = jobRepository;
        this.documentService = documentService;
        this.meterRegistry = meterRegistry;
        this.embedBatchSize = properties.embedBatchSize();
        this.staleAfter = Duration.ofSeconds(properties.staleAfterSeconds());
    }

    /**
     * Обрабатывает документ, если удаётся его захватить. Вызывается и нуджем от загрузки, и
     * поллером — claim гарантирует единственную обработку.
     */
    @Async("documentProcessingExecutor")
    public void process(UUID tenantId, UUID documentId) {
        Instant staleBefore = Instant.now().minus(staleAfter);
        if (!jobRepository.tryClaim(documentId, staleBefore)) {
            return;   // уже обрабатывается/обработан кем-то другим
        }

        TenantContext.set(tenantId);
        try {
            byte[] content = blobStorage.load(documentId)
                    .orElseThrow(() -> new IllegalStateException("Нет blob для документа " + documentId));

            String text = textExtractor.extract(content, documentId + ".pdf");
            List<TextChunk> chunks = chunker.chunk(text);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("Из документа не извлечено ни одного чанка");
            }

            chunkRepository.deleteByDocument(documentId);   // идемпотентность при переобработке
            chunkRepository.saveAll(embed(documentId, chunks));
            documentService.markDone(documentId);
            meterRegistry.counter("contract_audit.documents.processed", "status", "done").increment();
            log.info("Документ {} обработан: {} чанков", documentId, chunks.size());
        } catch (OcrRequiredException e) {
            log.warn("Документ {} требует OCR: {}", documentId, e.getMessage());
            markFailedQuietly(documentId, "ocr_required");
        } catch (RuntimeException e) {
            log.error("Ошибка обработки документа {}", documentId, e);
            markFailedQuietly(documentId, "error");
        } finally {
            TenantContext.clear();
        }
    }

    /** Эмбеддит чанки батчами и собирает их в {@link NewChunk} для сохранения. */
    private List<NewChunk> embed(UUID documentId, List<TextChunk> chunks) {
        List<NewChunk> result = new ArrayList<>(chunks.size());
        for (int from = 0; from < chunks.size(); from += embedBatchSize) {
            List<TextChunk> batch = chunks.subList(from, Math.min(from + embedBatchSize, chunks.size()));
            List<float[]> vectors = embeddingModel.embed(batch.stream().map(TextChunk::text).toList());
            for (int i = 0; i < batch.size(); i++) {
                TextChunk chunk = batch.get(i);
                result.add(new NewChunk(documentId, chunk.index(), chunk.text(), chunk.clauseRef(), vectors.get(i)));
            }
        }
        return result;
    }

    private void markFailedQuietly(UUID documentId, String reason) {
        meterRegistry.counter("contract_audit.documents.processed", "status", "failed", "reason", reason).increment();
        try {
            documentService.markFailed(documentId);
        } catch (RuntimeException ex) {
            log.error("Не удалось пометить документ {} как FAILED", documentId, ex);
        }
    }
}
