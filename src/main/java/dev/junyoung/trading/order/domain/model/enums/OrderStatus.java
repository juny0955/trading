package dev.junyoung.trading.order.domain.model.enums;

public enum OrderStatus {
    ACCEPTED,
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
    ;

    public boolean isFinal() {
        return this == CANCELLED || this == FILLED;
    }
}
