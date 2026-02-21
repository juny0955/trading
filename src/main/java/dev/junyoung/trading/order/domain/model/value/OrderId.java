package dev.junyoung.trading.order.domain.model.value;

import java.util.Objects;
import java.util.UUID;

public record OrderId(
    UUID value
) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be null or blank");
        }

        return new OrderId(UUID.fromString(raw));
    }
}
