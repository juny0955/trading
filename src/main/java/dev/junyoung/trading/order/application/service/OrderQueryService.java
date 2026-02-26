package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    @Override
    public OrderResult getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order Not Found"));

        return new OrderResult(
            order.getOrderId().toString(),
            order.getSide().name(),
            order.getPrice().value(),
            order.getQuantity().value(),
            order.getRemaining().value(),
            order.getStatus().name(),
            order.getOrderedAt()
        );
    }
}
