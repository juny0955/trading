package dev.junyoung.trading.order.application.port.in;

import dev.junyoung.trading.order.application.port.in.result.OrderResult;

public interface GetOrderUseCase {
    OrderResult getOrder(String orderId);
}
