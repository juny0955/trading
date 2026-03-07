package dev.junyoung.trading.order.domain.model.enums;

/** 주문 유효 조건. */
public enum TimeInForce {
    GTC, IOC, FOK;

    /** LIMIT 주문의 기본 유효 조건({@link #GTC})을 반환한다. */
    public static TimeInForce defaultValue() {
        return GTC;
    }
}
