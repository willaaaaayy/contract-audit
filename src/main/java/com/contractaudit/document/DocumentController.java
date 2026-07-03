package com.contractaudit.document;

import com.contractaudit.chunk.DocumentChunkRepository;
import com.contractaudit.common.upload.UploadValidator;
import com.contractaudit.document.processing.DocumentProcessingService;
import com.contractaudit.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST по документам. Все ответы автоматически ограничены текущим арендатором
 * (Hibernate {@code @TenantId}), поэтому чужой документ не отдаётся даже по прямому id.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentProcessingService processingService;
    private final DocumentChunkRepository chunkRepository;
    private final UploadValidator uploadValidator;

    public DocumentController(DocumentService documentService,
                              DocumentProcessingService processingService,
                              DocumentChunkRepository chunkRepository,
                              UploadValidator uploadValidator) {
        this.documentService = documentService;
        this.processingService = processingService;
        this.chunkRepository = chunkRepository;
        this.uploadValidator = uploadValidator;
    }

    /**
     * Загрузка договора: регистрируем метаданные и запускаем фоновую обработку
     * (extract → chunk → embed → store). Ответ 202 — обработка идёт асинхронно, статус
     * отслеживается через {@code GET /api/documents/{id}}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(@RequestParam("file") MultipartFile file,
                                   @AuthenticationPrincipal Jwt jwt) throws IOException {
        uploadValidator.validatePdf(file);
        UUID uploadedBy = UUID.fromString(jwt.getSubject());
        // Документ + blob коммитятся здесь → дальше он долговечен (поллер заберёт даже при краше).
        Document document = documentService.register(file.getOriginalFilename(), uploadedBy, file.getBytes());
        // Нудж для низкой задержки; tenantId с потока запроса. Безопасно — process() атомарно
        // захватывает документ, поэтому с поллером дубля не будет.
        processingService.process(TenantContext.require(), document.getId());
        return DocumentResponse.from(document);
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list().stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID id) {
        // 404 (а не 403), чтобы не палить существование чужого документа (docs/retrieval-design.md §1).
        return documentService.get(id)
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Извлечённый текст документа (склейка чанков) — питает DocumentViewer на экране аудита
     * (подсветка пункта по цитате) и сборку правленой версии из принятых предложений ИИ.
     * Tenant-scoped: {@code findChunkTextsByDocument} несёт {@code WHERE tenant_id}.
     */
    @GetMapping("/{id}/text")
    public ResponseEntity<DocumentTextResponse> text(@PathVariable UUID id) {
        if (documentService.get(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String text = String.join("\n\n", chunkRepository.findChunkTextsByDocument(id));
        return ResponseEntity.ok(new DocumentTextResponse(text));
    }

    /** Полный извлечённый текст документа. */
    public record DocumentTextResponse(String text) {
    }
}
