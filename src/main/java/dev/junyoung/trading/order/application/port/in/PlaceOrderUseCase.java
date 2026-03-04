package dev.junyoung.trading.order.application.port.in;

import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;

public interface PlaceOrderUseCase {
    String placeOrder(PlaceOrderCommand command);
}