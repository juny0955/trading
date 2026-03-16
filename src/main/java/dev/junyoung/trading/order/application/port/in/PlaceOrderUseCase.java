package dev.junyoung.trading.order.application.port.in;

import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.domain.model.value.OrderId;

public interface PlaceOrderUseCase {
    OrderId placeOrder(PlaceOrderCommand command);
}