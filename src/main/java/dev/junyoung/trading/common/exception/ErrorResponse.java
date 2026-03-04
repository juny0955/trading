package dev.junyoung.trading.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String errorCode,
    String message,
    String traceId,
    Instant timestamp,
    List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message, Object rejectedValue) { }

    public static ErrorResponse of(String errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode, message, traceId, Instant.now(), null);
    }

    public static ErrorResponse ofValidation(String errorCode, String message, String traceId, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode, message, traceId, Instant.now(), fieldErrors);
    }
}
