package dev.junyoung.trading.order.adapter.in.rest.response;

public record OrderResponse(
    String orderId,
    String side,
    long price,
    long quantity,
    long remaining,
    String status
) {
}
