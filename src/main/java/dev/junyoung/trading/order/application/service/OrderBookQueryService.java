package dev.junyoung.trading.order.application.service;

import org.springframework.stereotype.Service;

import dev.junyoung.trading.order.application.port.in.GetOrderBookUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import dev.junyoung.trading.shared.domain.entity.OrderBookSnapshot;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.shared.port.out.OrderBookCachePort;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderBookQueryService implements GetOrderBookUseCase {

    private final OrderBookCachePort orderBookCachePort;

    @Override
    public OrderBookResult getOrderBookCache(String symbol) {
        Symbol sym = new Symbol(symbol);
        OrderBookSnapshot snapshot = orderBookCachePort.getSnapshot(sym);
        return new OrderBookResult(snapshot.bids(), snapshot.asks());
    }
}
