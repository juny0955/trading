package dev.junyoung.trading.order.application.exception.order;

import dev.junyoung.trading.common.exception.base.BusinessException;

public class UnsupportedSymbolException extends BusinessException {
    public UnsupportedSymbolException(String symbol) {
        super(OrderErrorCode.UNSUPPORTED_SYMBOL, "Unsupported symbol: " + symbol);
    }
}
