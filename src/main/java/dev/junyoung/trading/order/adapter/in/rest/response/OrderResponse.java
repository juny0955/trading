package dev.junyoung.trading.order.adapter.in.rest.response;

import dev.junyoung.trading.order.application.port.in.result.OrderResult;

public record OrderResponse(
    String orderId,
    String side,
    long price,
    long quantity,
    long remaining,
    String status
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(
            result.orderId(),
            result.side(),
            result.price(),
            result.quantity(),
            result.remaining(),
            result.status()
        );
    }
}
