package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.adapter.out.cache.OrderBookCache;
import dev.junyoung.trading.order.adapter.out.cache.OrderBookSnapshot;
import dev.junyoung.trading.order.application.port.in.GetOrderBookUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderBookQueryService implements GetOrderBookUseCase {

    private final OrderBookCache orderBookCache;

    @Override
    public OrderBookResult getOrderBookCache(String symbol) {
        Symbol sym = new Symbol(symbol);
        OrderBookSnapshot snapshot = orderBookCache.getSnapshot(sym);
        return new OrderBookResult(snapshot.bids(), snapshot.asks());
    }
}
