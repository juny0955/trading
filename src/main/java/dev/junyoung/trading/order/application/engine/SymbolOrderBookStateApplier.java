package dev.junyoung.trading.order.application.engine;

import java.util.List;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.port.out.OrderBookStateApplier;
import dev.junyoung.trading.order.domain.model.OrderBook;
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
class SymbolOrderBookStateApplier implements OrderBookStateApplier {

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	private final OrderBook orderBook;
	private final OrderBookProjectionApplier delegate;

	SymbolOrderBookStateApplier(OrderBook orderBook, OrderBookProjectionApplier delegate) {
		this.orderBook = orderBook;
		this.delegate = delegate;
	}

	// -------------------------------------------------------------------------
	// 구현
	// -------------------------------------------------------------------------

	/**
	 * {@code ops}를 순서대로 live {@link OrderBook}에 반영한다.
	 *
	 * @param symbol 반영 대상 심볼 (로깅·검증 확장 여지를 위해 수신하나 현재는 위임 단계에서 불필요)
	 * @param ops    적용할 {@link BookOperation} 목록
	 */
	@Override
	public void apply(Symbol symbol, List<BookOperation> ops) {
		delegate.apply(orderBook, ops);
	}
}
