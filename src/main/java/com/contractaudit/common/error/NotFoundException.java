package com.contractaudit.common.error;

import org.springframework.http.HttpStatus;

/**
 * Ресурс не найден (или принадлежит другому арендатору — 404 вместо 403, чтобы не палить
 * существование чужих данных, см. docs/retrieval-design.md §1).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "not_found", message);
    }
}
