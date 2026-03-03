package dev.junyoung.trading.order.domain.model.enums;

public enum OrderType {
    LIMIT, MARKET;

    public boolean isMarket() {
        return this == MARKET;
    }
}
