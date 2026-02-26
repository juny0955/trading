package dev.junyoung.trading.order.application.port.in;

public interface CancelOrderUseCase {
    void cancelOrder(String orderId);
}
