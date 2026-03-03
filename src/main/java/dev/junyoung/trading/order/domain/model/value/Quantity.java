package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

public record Quantity(
    long value
) {

    public Quantity {
        if (value < 0)
            throw new BusinessRuleException("QUANTITY_NEGATIVE", "value must be positive");
    }

    public Quantity sub(Quantity executeQty) {
        return new Quantity(value - executeQty.value());
    }
}
