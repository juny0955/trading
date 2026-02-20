package dev.junyoung.trading.order.domain.model;

public record Quantity(
    long value
) {

    public Quantity {
        if (value < 0)
            throw new IllegalArgumentException("value must be positive");
    }

    public Quantity sub(Quantity executeQty) {
        return new Quantity(value - executeQty.value());
    }
}
