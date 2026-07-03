package com.contractaudit.common.error;

import org.springframework.http.HttpStatus;

/** Загруженный файл не прошёл валидацию (пустой, без имени, неподдерживаемый тип). */
public class InvalidUploadException extends ApiException {

    public InvalidUploadException(String message) {
        super(HttpStatus.BAD_REQUEST, "invalid_upload", message);
    }
}
