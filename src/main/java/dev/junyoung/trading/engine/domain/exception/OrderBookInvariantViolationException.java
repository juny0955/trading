package dev.junyoung.trading.engine.domain.exception;

public class OrderBookInvariantViolationException extends RuntimeException{
    public OrderBookInvariantViolationException(String message) {
        super(message);
    }
}
