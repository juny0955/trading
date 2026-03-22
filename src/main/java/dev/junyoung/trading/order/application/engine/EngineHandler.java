package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.adapter.out.cache.OrderBookCache;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.application.service.SettlementService;
import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.service.dto.CancelCalculationInput;
import dev.junyoung.trading.order.domain.service.dto.PlaceCalculationInput;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;

import java.util.List;

import dev.junyoung.trading.order.domain.service.state.OrderBookView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EngineCommand}를 수신해 {@link MatchingEngine}으로 디스패치하는 핸들러.
 *
 * <p>항상 engine-thread(단일 스레드)에서 호출된다. {@link EngineLoop}가 큐에서 커맨드를
 * 꺼내 이 클래스로 전달하므로, 내부 연산은 별도의 동기화 없이 안전하다.</p>
 *
 * <p>{@link EngineCommand}가 {@code sealed interface}이므로 switch 패턴 매칭이
 * 컴파일 타임에 완전성을 검사한다. 새 커맨드 타입 추가 시 여기에도 case를 추가해야 한다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class EngineHandler {

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	/** 이 핸들러가 처리하는 심볼. {@link OrderBookCache} 업데이트 시 키로 사용된다. */
	private final Symbol symbol;
	private final MatchingEngine engine;
	private final OrderBook orderBook;
	private final OrderBookCachePort orderBookCachePort;
	private final SettlementService settlementService;

	// -------------------------------------------------------------------------
	// 진입점
	// -------------------------------------------------------------------------

	/**
	 * 커맨드 타입에 따라 엔진 동작을 실행한다.
	 *
	 * <ul>
	 *   <li>{@link EngineCommand.PlaceOrder}: 주문을 매칭 엔진에 전달하고 체결 결과를 저장한다.
	 *       taker/maker 상태 변경은 참조 공유로 OrderRepository에 자동 반영된다 (in-memory MVP).</li>
	 *   <li>{@link EngineCommand.CancelOrder}: 호가창에서 주문을 제거하고 상태를 CANCELLED로 전이 후 명시적 save.</li>
	 * </ul>
	 */
	protected void handle(EngineCommand command) {
		switch (command) {
			case EngineCommand.PlaceOrder c -> handlePlaceOrder(c.order());
			case EngineCommand.CancelOrder c -> handleCancelOrder(c.orderId());
			case EngineCommand.Shutdown _ ->
				// EngineLoop.run()이 직접 처리하므로 여기까지 오면 로직 오류
				log.warn("Shutdown command reached EngineHandler; this should not happen.");
		}
	}

	// -------------------------------------------------------------------------
	// 내부 헬퍼
	// -------------------------------------------------------------------------

	private void handlePlaceOrder(Order order) {
		PlaceResult result = processPlaceOrder(order);
		settlementService.settlement(result);
		orderBookCachePort.update(symbol, orderBook);
	}

	private void handleCancelOrder(OrderId orderId) {
		Order order = orderBook.getIndex().get(orderId);
		if (order == null) {
			log.warn("Cancel skipped - order not in book: orderId={}", orderId);
			return;
		}

		OrderBookView view = OrderBookViewFactory.create(orderBook);
		CancelCalculationResult result = engine.calculateCancel(new CancelCalculationInput(view, order));

		switch (result) {
			case CancelCalculationResult.Cancelled c -> {
				applyBookOps(c.bookOps());
				settlementService.cancelSettlement(c.updatedOrders().getFirst());
				orderBookCachePort.update(symbol, orderBook);
			}
			case CancelCalculationResult.Skipped s ->
				log.warn("Cancel skipped - order already final: symbol={}, seq={}", s.symbol(), s.acceptedSeq());
			case CancelCalculationResult.Rejected r ->
				log.warn("Cancel rejected: symbol={}, seq={}, reason={}", r.symbol(), r.acceptedSeq(), r.reasonCode());
		}
	}

	/**
	 * {@link MatchingEngine#calculatePlace}를 호출해 변경안을 계산하고,
	 * bookOps를 live {@link OrderBook}에 반영한 뒤 {@link PlaceResult}로 변환한다.
	 */
	private PlaceResult processPlaceOrder(Order order) {
		OrderBookView view = OrderBookViewFactory.create(orderBook);
		PlaceCalculationResult result = engine.calculatePlace(new PlaceCalculationInput(view, order));

		return switch (result) {
			case PlaceCalculationResult.Rejected r -> {
				log.warn("Order rejected: symbol={}, seq={}, reason={}", r.symbol(), r.acceptedSeq(), r.reasonCode());
				yield PlaceResult.empty();
			}
			case PlaceCalculationResult.Accepted a -> {
				applyBookOps(a.bookOps());
				yield PlaceResult.of(a.updatedOrders(), a.trades());
			}
		};
	}

	private void applyBookOps(List<BookOperation> ops) {
		for (BookOperation op : ops) {
			switch (op) {
				case BookOperation.Add a -> orderBook.add(a.order());
				case BookOperation.Replace r -> orderBook.replaceOrder(r.updatedOrder());
				case BookOperation.Remove r -> orderBook.remove(r.orderId());
			}
		}
	}
}
