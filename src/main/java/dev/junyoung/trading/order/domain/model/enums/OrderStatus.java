package dev.junyoung.trading.order.domain.model.enums;

/** 주문 생명주기 상태. */
public enum OrderStatus {
    ACCEPTED,
    NEW,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED
    ;

    /** 종료 상태({@link #CANCELLED} 또는 {@link #FILLED}) 여부를 반환한다. */
    public boolean isFinal() {
        return this == CANCELLED || this == FILLED;
    }

    /** 활설 상태({@link #NEW} 또는 {@link #PARTIALLY_FILLED}) 여부를 반환한다. */
    public boolean isActive() {
        return this == NEW || this == PARTIALLY_FILLED;
    }
}
