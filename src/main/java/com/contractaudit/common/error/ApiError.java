package com.contractaudit.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Единый конверт ошибки API: {@code error} — машинный код (snake_case), {@code message} —
 * человекочитаемое описание, {@code details} — попольные нарушения (только для валидации).
 * Совместим по форме с ответом {@code RateLimitFilter} ({@code {"error":"too_many_requests"}}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, List<FieldViolation> details) {

    /** Нарушение валидации конкретного поля запроса. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }
}
