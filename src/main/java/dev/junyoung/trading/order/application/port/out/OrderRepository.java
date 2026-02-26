package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.domain.model.entity.Order;

import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String id);
}
