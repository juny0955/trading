package dev.junyoung.trading.order.adapter.in.rest.reqeust;

public record PlaceOrderRequest(
    String side,
    long price,
    long quantity
) {
}
