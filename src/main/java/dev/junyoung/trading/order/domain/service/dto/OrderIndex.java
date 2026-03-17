package dev.junyoung.trading.order.domain.service.dto;

import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record OrderIndex (Map<OrderId, Order> orderIndex) {
    public static OrderIndex of(List<Order> orders) {
        Map<OrderId, Order> orderMap = new HashMap<>();
        for (Order order : orders) {
            orderMap.put(order.getOrderId(), order);
        }

        return new OrderIndex(orderMap);
    }

    public Order findOrder(OrderId orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null)
            throw new BusinessRuleException("SETTLEMENT_ORDER_NOT_FOUND", "updatedOrders missing trade order: " + orderId);
        return order;
    }
}
