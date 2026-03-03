package dev.junyoung.trading.order.adapter.in.rest.response;

import dev.junyoung.trading.order.application.port.in.result.OrderResult;

import java.time.Instant;

public record OrderResponse(
    String orderId,
    String side,
    Long price,
    long quantity,
    long remaining,
    String status,
    Instant orderedAt
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(
            result.orderId(),
            result.side(),
            result.price(),
            result.quantity(),
            result.remaining(),
            result.status(),
            result.orderedAt()
        );
    }
}
