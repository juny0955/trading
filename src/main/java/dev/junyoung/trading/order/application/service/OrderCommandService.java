package dev.junyoung.trading.order.application.service;

import org.springframework.stereotype.Service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.exception.OrderAlreadyFinalizedException;
import dev.junyoung.trading.order.application.exception.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final EngineManager engineManager;
    private final OrderRepository orderRepository;

    @Override
    public String placeOrder(PlaceOrderCommand command) {
        Order order = Order.create(command.symbol(),
            command.side(),
            command.orderType(),
            command.tif(),
            command.price(),
            command.quoteQty(),
            command.quantity()
        );

        engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));
        orderRepository.save(order);  // ACCEPTED 상태로 최초 저장 (참조 공유로 이후 상태 변경 자동 반영)
        return order.getOrderId().toString();
    }

    @Override
    public void cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.isMarket())
            throw new OrderNotCancellableException(orderId);

        if (order.getStatus().isFinal())
            throw new OrderAlreadyFinalizedException(orderId);

        engineManager.submit(order.getSymbol(), new EngineCommand.CancelOrder(OrderId.from(orderId)));
    }
}
