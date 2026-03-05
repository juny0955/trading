package dev.junyoung.trading.order.domain.model.enums;

public enum OrderType {
    LIMIT, MARKET;

    public boolean isMarket() {
        return this == MARKET;
    }

    public static boolean isMarket(String raw) {
        return MARKET.name().equals(raw);
    }
}
