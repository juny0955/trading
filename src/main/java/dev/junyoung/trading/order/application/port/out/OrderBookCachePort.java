package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.adapter.out.cache.OrderBookSnapshot;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public interface OrderBookCachePort {
    void update(Symbol symbol, OrderBook orderBook);
    OrderBookSnapshot getSnapshot(Symbol symbol);
}
