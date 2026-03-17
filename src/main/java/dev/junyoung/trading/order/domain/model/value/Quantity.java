package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

/** 주문 수량 (0 이상의 정수). */
public record Quantity(
    long value
) {

    public Quantity {
        if (value < 0)
            throw new BusinessRuleException("QUANTITY_NEGATIVE", "value must be positive");
    }

    /** 체결 수량을 차감한 새 Quantity를 반환한다. */
    public Quantity sub(Quantity executeQty) {
        return new Quantity(value - executeQty.value());
    }

    public Quantity add(long executeQty) {
        return new Quantity(Math.addExact(value, executeQty));
    }

    public boolean isPositive() {
        return value > 0;
    }
}
