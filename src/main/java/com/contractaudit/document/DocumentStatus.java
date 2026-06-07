package com.contractaudit.document;

/** Статус асинхронной обработки документа. См. docs/retrieval-design.md (пайплайн обработки PDF). */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED
}
