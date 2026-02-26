package dev.junyoung.trading.order.application.port.in.result;

import java.time.Instant;

public record OrderResult(
    String orderId,
    String side,
    long price,
    long quantity,
    long remaining,
    String status,
    Instant orderedAt
) {
}
