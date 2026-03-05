package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.port.in.GetOrderUseCase;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.application.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService implements GetOrderUseCase {

    private final OrderRepository orderRepository;

    @Override
    public OrderResult getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Long quantity = order.getQuantity() == null ? null : order.getQuantity().value();

        return new OrderResult(
            order.getOrderId().toString(),
            order.getSide().name(),
            order.isMarket() ? null : order.getLimitPriceOrThrow().value(),
            quantity,
            order.getRemaining().value(),
            order.getStatus().name(),
            order.getOrderedAt()
        );
    }
}
