package dev.junyoung.trading.order.application.port.in.result;

import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

import java.util.NavigableMap;

public record OrderBookResult(
    NavigableMap<Price, Quantity> bids,
    NavigableMap<Price, Quantity> asks
) {
}
