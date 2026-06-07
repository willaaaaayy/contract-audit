package com.contractaudit.document;

import java.time.Instant;
import java.util.UUID;

/** Представление документа для API. */
public record DocumentResponse(UUID id, String filename, DocumentStatus status, Instant createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getCreatedAt());
    }
}
