package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineLoop;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final EngineLoop engineLoop;

    @Override
    public String placeOrder(String side, long price, long quantity) {
        Order order = new Order(Side.valueOf(side), new Price(price), new Quantity(quantity));
        engineLoop.submit(new EngineCommand.PlaceOrder(order));
        return order.getOrderId().toString();
    }

    @Override
    public void cancelOrder(String orderId) {
        engineLoop.submit(new EngineCommand.CancelOrder(OrderId.from(orderId)));
    }
}
