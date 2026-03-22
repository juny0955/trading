package dev.junyoung.trading.order.application.engine;

import java.util.List;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.port.out.OrderBookStateApplier;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;

class SymbolOrderBookStateApplier implements OrderBookStateApplier {

	private final OrderBook orderBook;
	private final OrderBookProjectionApplier delegate;

	SymbolOrderBookStateApplier(OrderBook orderBook, OrderBookProjectionApplier delegate) {
		this.orderBook = orderBook;
		this.delegate = delegate;
	}

	@Override
	public void apply(Symbol symbol, List<BookOperation> ops) {
		delegate.apply(orderBook, ops);
	}
}
