package dev.junyoung.trading.order.application.port.in;

public interface PlaceOrderUseCase {
    String placeOrder(String symbol, String side, String orderType, long price, long quantity);
}