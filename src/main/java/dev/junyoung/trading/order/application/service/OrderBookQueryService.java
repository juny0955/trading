package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.engine.OrderBookCache;
import dev.junyoung.trading.order.application.port.in.GetOrderBookUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderBookQueryService implements GetOrderBookUseCase {

    private final OrderBookCache orderBookCache;

    @Override
    public OrderBookResult getOrderBookCache() {
        return new OrderBookResult(orderBookCache.latestBids(), orderBookCache.latestAsks());
    }
}
