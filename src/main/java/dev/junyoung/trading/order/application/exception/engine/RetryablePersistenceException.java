package dev.junyoung.trading.order.application.exception.engine;

public class RetryablePersistenceException extends RuntimeException {
    public RetryablePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
