package com.contractaudit.common.error;

import org.springframework.http.HttpStatus;

/**
 * Базовое доменное исключение API: несёт HTTP-статус и машинный код ошибки.
 * Транслируется в {@link ApiError} глобальным обработчиком {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
