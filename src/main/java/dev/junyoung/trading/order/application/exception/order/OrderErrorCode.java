package dev.junyoung.trading.order.application.exception.order;

import dev.junyoung.trading.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"),
    UNSUPPORTED_SYMBOL(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SYMBOL", "Unsupported symbol");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
