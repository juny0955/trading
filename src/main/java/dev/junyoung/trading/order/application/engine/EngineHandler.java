package dev.junyoung.trading.order.application.engine;

import java.util.List;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.adapter.out.cache.OrderBookCache;
import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.exception.engine.PersistenceInvariantViolationException;
import dev.junyoung.trading.order.application.exception.engine.RetryablePersistenceException;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.application.port.out.OrderBookStateApplier;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.service.dto.CancelCalculationInput;
import dev.junyoung.trading.order.domain.service.dto.PlaceCalculationInput;
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
	private final OrderBookStateApplier orderBookStateApplier;
	private final OrderBookCachePort orderBookCachePort;
	private final EngineResultPersistenceService engineResultPersistenceService;
	private final EngineRuntimeOwner runtimeOwner;

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
		if (runtimeOwner.state() != EngineSymbolState.ACTIVE) {
			log.warn("Command dropped: engine not ACTIVE: state={}, symbol={}", runtimeOwner.state(), symbol);
			return;
		}

		switch (command) {
			case EngineCommand.PlaceOrder c -> handlePlaceOrder(c.order());
			case EngineCommand.CancelOrder c -> handleCancelOrder(c.orderId(), c.requesterAccountId());
			case EngineCommand.Shutdown _ ->
				// EngineLoop.run()이 직접 처리하므로 여기까지 오면 로직 오류
				log.warn("Shutdown command reached EngineHandler; this should not happen.");
		}
	}

	// -------------------------------------------------------------------------
	// 내부 헬퍼
	// -------------------------------------------------------------------------

	private void handlePlaceOrder(Order order) {
		OrderBookView view = OrderBookViewFactory.create(orderBook);
		PlaceCalculationResult result;
		try {
			result = engine.calculatePlace(new PlaceCalculationInput(view, order));
		} catch (Exception e) {
			runtimeOwner.transitionToDirty();
			throw e;
		}

		switch (result) {
			case PlaceCalculationResult.Accepted a -> {
				persistPlace(a);
				applyToOrderBook(a.bookOps());
				updateCache();
			}
			case PlaceCalculationResult.Rejected r ->
				log.warn("Order rejected: symbol={}, seq={}, reason={}", r.symbol(), r.acceptedSeq(), r.reasonCode());
		}
	}

	private void handleCancelOrder(OrderId orderId, AccountId requesterAccountId) {
		Order order = orderBook.getIndex().get(orderId);
		OrderBookView view = OrderBookViewFactory.create(orderBook);
		CancelCalculationResult result;

		try {
			result = engine.calculateCancel(new CancelCalculationInput(view, symbol, orderId, requesterAccountId, order));
		} catch (Exception e) {
			runtimeOwner.transitionToDirty();
			throw e;
		}

		switch (result) {
			case CancelCalculationResult.Cancelled c -> {
				persistCancel(c);
				applyToOrderBook(c.bookOps());
				updateCache();
			}
			case CancelCalculationResult.Skipped s ->
				log.warn("Cancel skipped - order already final: symbol={}, seq={}", s.symbol(), s.acceptedSeq());
			case CancelCalculationResult.Rejected r ->
				log.warn("Cancel rejected: symbol={}, seq={}, reason={}", r.symbol(), r.acceptedSeq(), r.reasonCode());
		}
	}

	private void persistPlace(PlaceCalculationResult.Accepted accepted) {
		try {
			engineResultPersistenceService.persistPlaceResult(accepted);
		} catch (RetryablePersistenceException e) {
			// retryable 실패는 live book을 건드리지 않은 채 상위로 전파한다.
			throw e;
		} catch (PersistenceInvariantViolationException e) {
			// persistence 모델과 계산 결과가 어긋난 경우는 즉시 DIRTY로 격리한다.
			runtimeOwner.transitionToDirty();
			throw e;
		}
	}

	private void persistCancel(CancelCalculationResult.Cancelled cancelled) {
		try {
			engineResultPersistenceService.persistCancelResult(cancelled);
		} catch (RetryablePersistenceException e) {
			// retryable 실패는 live book을 건드리지 않은 채 상위로 전파한다.
			throw e;
		} catch (PersistenceInvariantViolationException e) {
			// persistence 모델과 계산 결과가 어긋난 경우는 즉시 DIRTY로 격리한다.
			runtimeOwner.transitionToDirty();
			throw e;
		}
	}

	private void applyToOrderBook(List<BookOperation> ops) {
		try {
			orderBookStateApplier.apply(symbol, ops);
		} catch (Exception e) {
			// DB 반영 이후 live projection이 실패하면 rebuild만이 일관성 복구 수단이다.
			runtimeOwner.transitionToRebuilding();
			throw e;
		}
	}

	private void updateCache() {
		try {
			orderBookCachePort.update(symbol, orderBook);
		} catch (Exception e) {
			log.error("Cache update failed after apply: symbol={}", symbol, e);
		}
	}
}
