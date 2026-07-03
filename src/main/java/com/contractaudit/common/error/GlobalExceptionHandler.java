package com.contractaudit.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Единый обработчик ошибок API: любое исключение превращается в {@link ApiError}
 * с корректным HTTP-статусом. Доменные предусловия ({@link ApiException}) — 4xx,
 * ошибки валидации — 400 с попольными деталями, всё непредвиденное — 500 без
 * утечки внутренностей (стектрейс остаётся в логе).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> apiException(ApiException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        List<ApiError.FieldViolation> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", "Запрос не прошёл валидацию", details));
    }

    /** Кривое значение path/query-параметра (например, не-UUID в {id}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiError.of("invalid_parameter",
                "Некорректное значение параметра '%s'".formatted(e.getName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("malformed_request", "Тело запроса не удалось разобрать"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> missingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(ApiError.of("missing_file",
                "Отсутствует обязательная часть запроса '%s'".formatted(e.getRequestPartName())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of("payload_too_large", "Файл превышает допустимый размер"));
    }

    /** Унифицирует уже существующие ручные ResponseStatusException (auth/user) без их правки. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException e) {
        String code = e.getStatusCode().is4xxClientError()
                ? HttpStatus.valueOf(e.getStatusCode().value()).name().toLowerCase()
                : "error";
        return ResponseEntity.status(e.getStatusCode()).body(ApiError.of(code, e.getReason()));
    }

    /** Не перехватываем: должен дойти до Spring Security и стать 403 (@PreAuthorize). */
    @ExceptionHandler(AccessDeniedException.class)
    public void accessDenied(AccessDeniedException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internal(Exception e) {
        log.error("Необработанная ошибка API", e);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("internal_error", "Внутренняя ошибка сервера"));
    }
}
