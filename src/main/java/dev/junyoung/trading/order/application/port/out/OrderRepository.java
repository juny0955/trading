package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Symbol;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    void updateAll(List<Order> orders);

    Optional<Order> findById(OrderId id);
    Optional<Long> findMaxAcceptedSeq();

    List<Order> findOpenOrdersBySymbol(Symbol symbol);

    void deleteById(OrderId orderId);
}
