package dev.junyoung.trading.order.domain.model.enums;

public enum TimeInForce {
    GTC, IOC, FOK;

    public static TimeInForce defaultValue() {
        return GTC;
    }
}
