package dev.junyoung.trading.order.application.exception.engine;

public class PersistenceInvariantViolationException extends RuntimeException {
    public PersistenceInvariantViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
