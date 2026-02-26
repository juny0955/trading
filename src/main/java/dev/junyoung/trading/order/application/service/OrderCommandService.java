package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineLoop;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final EngineLoop engineLoop;
    private final OrderRepository orderRepository;

    @Override
    public String placeOrder(String symbol, String side, String orderType, Long price, long quantity) {
        Order order = Order.create(symbol, side, orderType, price, quantity);
        orderRepository.save(order);  // ACCEPTED 상태로 최초 저장 (참조 공유로 이후 상태 변경 자동 반영)
        engineLoop.submit(new EngineCommand.PlaceOrder(order));
        return order.getOrderId().toString();
    }

    @Override
    public void cancelOrder(String orderId) {
        engineLoop.submit(new EngineCommand.CancelOrder(OrderId.from(orderId)));
    }
}
