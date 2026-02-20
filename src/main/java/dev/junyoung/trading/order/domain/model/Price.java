package dev.junyoung.trading.order.domain.model;

public record Price(
    long value
) {

    public Price {
        if (value < 1) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
