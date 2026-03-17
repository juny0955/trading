package dev.junyoung.trading.order.domain.service.dto;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;

public record OrderIndex (Map<OrderId, Order> orderIndex) {
    public static OrderIndex of(List<Order> orders) {
        return new OrderIndex(orders.stream()
            .collect(Collectors.toMap(Order::getOrderId, Function.identity())));
    }

    public Order findOrder(OrderId orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null)
            throw new BusinessRuleException("SETTLEMENT_ORDER_NOT_FOUND", "updatedOrders missing trade order: " + orderId);
        return order;
    }
}
