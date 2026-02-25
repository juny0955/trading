package dev.junyoung.trading.order.application.port.in;

import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;

public interface GetOrderBookUseCase {
    OrderBookResult getOrderBookCache();
}
