package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;

import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);

    Optional<Long> findMaxAcceptedSeq();

    void deleteById(OrderId orderId);
}
