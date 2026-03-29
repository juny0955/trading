package dev.junyoung.trading.shared.port.out;

import dev.junyoung.trading.shared.domain.entity.OrderBookSnapshot;
import dev.junyoung.trading.shared.domain.value.Symbol;

public interface OrderBookCachePort {
    void update(Symbol symbol, OrderBookSnapshot orderBookSnapshot);
    OrderBookSnapshot getSnapshot(Symbol symbol);
}
