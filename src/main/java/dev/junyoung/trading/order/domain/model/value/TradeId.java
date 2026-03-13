package dev.junyoung.trading.order.domain.model.value;

import java.util.Objects;
import java.util.UUID;

public record TradeId(
    UUID value
) {
    public TradeId {
        Objects.requireNonNull(value, "value");
    }

    public static TradeId newId() {
        return new TradeId(UUID.randomUUID());
    }
}
