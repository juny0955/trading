package dev.junyoung.trading.order.domain.model.enums;

/** 매매 방향. */
public enum Side {
    BUY, SELL;

    /** 문자열이 BUY를 나타내는지 반환한다. */
    public static boolean isBuy(String raw) {
        return BUY.name().equals(raw);
    }

    public boolean isBuy() {
        return this == BUY;
    }

    public boolean isSell() {
        return this == SELL;
    }

    /** 반대 사이드를 반환한다. BUY → SELL, SELL → BUY. */
    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }

}
