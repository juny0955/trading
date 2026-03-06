package dev.junyoung.trading.order.domain.model.enums;

/** 주문 유형 (지정가/시장가). */
public enum OrderType {
    LIMIT, MARKET;

    /** 시장가 주문 여부를 반환한다. */
    public boolean isMarket() {
        return this == MARKET;
    }

    public static boolean isMarket(String raw) {
        return MARKET.name().equals(raw);
    }
}
