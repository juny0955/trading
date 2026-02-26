package dev.junyoung.trading.order.application.port.in.result;

import java.util.NavigableMap;

public record OrderBookResult(
    NavigableMap<Long, Long> bids,
    NavigableMap<Long, Long> asks
) {
}
