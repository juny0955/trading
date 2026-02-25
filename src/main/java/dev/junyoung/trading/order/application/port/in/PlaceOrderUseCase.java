package dev.junyoung.trading.order.application.port.in;

public interface PlaceOrderUseCase {
    String placeOrder(String side, long price, long quantity);
}