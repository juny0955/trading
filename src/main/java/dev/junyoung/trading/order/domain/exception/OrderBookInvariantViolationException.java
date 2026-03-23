package dev.junyoung.trading.order.domain.exception;

public class OrderBookInvariantViolationException extends RuntimeException{
    public OrderBookInvariantViolationException(String message) {
        super(message);
    }
}
