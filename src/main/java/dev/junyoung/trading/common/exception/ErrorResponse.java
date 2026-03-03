package dev.junyoung.trading.common.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String errorCode,
    String message,
    String traceId,
    Instant timestamp,
    List<ValidationException.FieldError> fieldErrors
) {

    public static ErrorResponse of(String errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode, message, traceId, Instant.now(), null);
    }

    public static ErrorResponse ofValidation(String errorCode, String message, String traceId,
                                              List<ValidationException.FieldError> fieldErrors) {
        return new ErrorResponse(errorCode, message, traceId, Instant.now(), fieldErrors);
    }
}
