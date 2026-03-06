package dev.junyoung.trading.order.application.exception.order;

import dev.junyoung.trading.common.exception.BusinessRuleException;

public class OrderNotCancellableException extends BusinessRuleException {
    public OrderNotCancellableException(String orderId) {
        super("ORDER_NOT_CANCELLABLE", "Order is not cancellable (MARKET order): " + orderId);
    }
}
