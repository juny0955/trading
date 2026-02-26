package dev.junyoung.trading.order.adapter.in.rest.request;

public record PlaceOrderRequest(
    String symbol,
    String side,
    String orderType,
    long price,
    long quantity
) {
}
