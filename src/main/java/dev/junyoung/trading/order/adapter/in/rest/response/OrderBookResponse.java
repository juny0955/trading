package dev.junyoung.trading.order.adapter.in.rest.response;

import java.util.List;

public record OrderBookResponse(
    List<PriceLevel> bids,
    List<PriceLevel> asks
) {
    public record PriceLevel(long price, long quantity) { }
}
