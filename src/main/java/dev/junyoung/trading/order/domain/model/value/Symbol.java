package dev.junyoung.trading.order.domain.model.value;

import java.util.Objects;

public record Symbol(
    String value
) {
    public Symbol {
        Objects.requireNonNull(value, "symbol must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        value = value.toUpperCase();
    }
}
