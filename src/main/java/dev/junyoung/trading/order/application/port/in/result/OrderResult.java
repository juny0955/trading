package dev.junyoung.trading.order.application.port.in.result;

import java.time.Instant;

public record OrderResult(
    String orderId,
    String side,
    Long price,
    Long quantity,
    long remaining,
    String status,
    Instant orderedAt,
    Long requestedQuoteQty,
    Long requestedQty,
    Long cumQuoteQty,
    Long cumBaseQty,
    Long leftoverQuoteQty
) {
}
