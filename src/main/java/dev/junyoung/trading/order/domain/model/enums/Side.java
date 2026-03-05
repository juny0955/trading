package dev.junyoung.trading.order.domain.model.enums;

public enum Side {
    BUY, SELL;

    public static boolean isBuy(String raw) {
        return BUY.name().equals(raw);
    }

    public boolean isBuy() {
        return this == BUY;
    }

    public boolean isSell() {
        return this == SELL;
    }

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }

}
