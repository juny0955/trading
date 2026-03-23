package dev.junyoung.trading.order.application.exception.engine;

import dev.junyoung.trading.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum EngineErrorCode implements ErrorCode {
    ENGINE_BACKPRESSURE(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_BACKPRESSURE", "engine is busy"),
    ENGINE_NOT_ACTIVE(HttpStatus.SERVICE_UNAVAILABLE, "ENGINE_NOT_ACTIVE", "engine is not active"),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
