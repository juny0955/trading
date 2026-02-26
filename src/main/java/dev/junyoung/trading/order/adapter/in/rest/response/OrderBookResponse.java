package dev.junyoung.trading.order.adapter.in.rest.response;

import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;

import java.util.List;

public record OrderBookResponse(
    List<PriceLevel> bids,
    List<PriceLevel> asks
) {
    public record PriceLevel(long price, long quantity) { }

    public static OrderBookResponse from(OrderBookResult result) {
        return new OrderBookResponse(
            result.bids().entrySet().stream()
                .map(e -> new PriceLevel(e.getKey(), e.getValue()))
                .toList(),
            result.asks().entrySet().stream()
                .map(e -> new PriceLevel(e.getKey(), e.getValue()))
                .toList()
        );
    }
}
