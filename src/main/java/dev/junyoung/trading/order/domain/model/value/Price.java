package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

public record Price(
    long value
) {
    public Price {
        if (value < 1) {
            throw new BusinessRuleException("PRICE_INVALID", "price must be positive");
        }
    }
}
