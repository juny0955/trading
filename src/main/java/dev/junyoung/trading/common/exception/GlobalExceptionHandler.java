package dev.junyoung.trading.common.exception;

import dev.junyoung.trading.common.exception.base.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        log.warn("[{}] Validation failed: {}", e.getErrorCode().code(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.ofValidation(
                        e.getErrorCode().code(), e.getMessage(), traceId(), e.getFieldErrors()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        log.warn("[{}] Not found: {}", e.getErrorCode().code(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(e.getErrorCode().code(), e.getMessage(), traceId()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("[{}] Business error: {}", e.getErrorCode().code(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().status())
                .body(ErrorResponse.of(e.getErrorCode().code(), e.getMessage(), traceId()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        log.warn("[{}] Conflict: {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage(), traceId()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        log.warn("[{}] Business rule violated: {}", e.getErrorCode(), e.getMessage());
        return ResponseEntity
                .status(HttpStatusCode.valueOf(422))
                .body(ErrorResponse.of(e.getErrorCode(), e.getMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Unexpected error occurred", traceId()));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}
