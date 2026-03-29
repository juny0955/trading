package dev.junyoung.trading.engine.application.engine.book;

import java.util.List;

import dev.junyoung.trading.engine.domain.model.BookOperation;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public interface OrderBookStateApplier {
	void apply(Symbol symbol, List<BookOperation> ops);
}
