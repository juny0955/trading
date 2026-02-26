package dev.junyoung.trading.order.adapter.in.rest.request;

public record PlaceOrderRequest(
    String side,
    long price,
    long quantity
) {
}
