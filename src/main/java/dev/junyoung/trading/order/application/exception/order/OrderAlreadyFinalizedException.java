package dev.junyoung.trading.order.application.exception.order;

import dev.junyoung.trading.common.exception.ConflictException;

public class OrderAlreadyFinalizedException extends ConflictException {
    public OrderAlreadyFinalizedException(String orderId) {
        super("ORDER_ALREADY_FINALIZED", "Order is already finalized: " + orderId);
    }
}
