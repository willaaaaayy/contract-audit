package com.contractaudit.common.error;

import org.springframework.http.HttpStatus;

/** Конфликт состояния: операция невозможна, пока не выполнено предварительное условие. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "conflict", message);
    }
}
