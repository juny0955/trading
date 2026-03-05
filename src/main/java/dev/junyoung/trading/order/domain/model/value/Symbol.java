package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

import java.util.Objects;

public record Symbol(
    String value
) {
    public Symbol {
        Objects.requireNonNull(value, "symbol must not be null");
        if (value.isBlank())
            throw new BusinessRuleException("SYMBOL_BLANK", "symbol must not be blank");

        value = value.toUpperCase();
    }
}
