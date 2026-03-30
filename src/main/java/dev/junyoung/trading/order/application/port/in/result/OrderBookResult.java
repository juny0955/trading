package dev.junyoung.trading.order.application.port.in.result;

import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;

import java.util.NavigableMap;

public record OrderBookResult(
    NavigableMap<Price, Quantity> bids,
    NavigableMap<Price, Quantity> asks
) {
}
