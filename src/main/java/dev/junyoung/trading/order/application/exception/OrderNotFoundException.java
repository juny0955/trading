package dev.junyoung.trading.order.application.exception;

import dev.junyoung.trading.common.exception.NotFoundException;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(String orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
    }
}
