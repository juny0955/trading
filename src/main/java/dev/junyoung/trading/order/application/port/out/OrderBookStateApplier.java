package dev.junyoung.trading.order.application.port.out;

import java.util.List;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public interface OrderBookStateApplier {
	void apply(Symbol symbol, List<BookOperation> ops);
}
