package dev.junyoung.trading.engine.application.book;

import java.util.List;

import dev.junyoung.trading.engine.application.dto.BookOperation;
import dev.junyoung.trading.engine.application.runtime.EngineRuntime;
import dev.junyoung.trading.engine.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;

/**
 * 특정 심볼의 live {@link OrderBook}에 {@link BookOperation} 목록을 반영하는 {@link OrderBookStateApplier} 구현체.
 *
 * <p>Spring Bean이 아니며 {@link EngineRuntime}이 심볼별로 직접 생성한다.
 * 실제 반영은 {@link OrderBookProjectionApplier}에 위임하고,
 * 이 클래스는 {@link OrderBook} 인스턴스를 {@link OrderBookStateApplier} 인터페이스에 바인딩하는 역할만 담당한다.</p>
 *
 * <p>외부 진입점은 {@link #apply(Symbol, List)}이며, 항상 engine-thread에서 호출된다.</p>
 */
public class SymbolOrderBookStateApplier implements OrderBookStateApplier {

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	private final OrderBook orderBook;
	private final OrderBookProjectionApplier delegate;

	public SymbolOrderBookStateApplier(OrderBook orderBook, OrderBookProjectionApplier delegate) {
		this.orderBook = orderBook;
		this.delegate = delegate;
	}

	// -------------------------------------------------------------------------
	// 구현
	// -------------------------------------------------------------------------

	@Override
	public void apply(Symbol symbol, List<BookOperation> ops) {
		delegate.apply(orderBook, ops);
	}
}
