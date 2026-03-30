package dev.junyoung.trading.engine.application.book;

import java.util.List;

import dev.junyoung.trading.engine.application.dto.BookOperation;
import dev.junyoung.trading.shared.domain.value.Symbol;

public interface OrderBookStateApplier {
	void apply(Symbol symbol, List<BookOperation> ops);
}
