package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceRejectCode;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.domain.exception.OrderBookInvariantViolationException;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.service.MatchingEngineTest;
import dev.junyoung.trading.order.fixture.OrderFixture;

/**
 * {@link EngineHandler} 단위 테스트.
 *
 * <p>{@link MatchingEngine}을 mock으로 대체해 커맨드 타입별 디스패치 동작만 검증한다.
 * 실제 매칭 로직은 {@link MatchingEngineTest}에서 별도 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineHandler")
class EngineHandlerTest {

	@Mock
	private MatchingEngine engine;

	@Mock
	private OrderBook orderBook;

	@Mock
	private OrderBookProjectionApplier orderBookProjectionApplier;

	@Mock
	private OrderBookCachePort orderBookCachePort;

	@Mock
	private EngineResultPersistenceService engineResultPersistenceService;

	@Mock
	private EngineRuntimeOwner runtimeOwner;

	private EngineHandler handler;

	private static final Symbol SYMBOL = new Symbol("BTC");

	@BeforeEach
	void setUp() {
		// OrderBookViewFactory.create(orderBook) 에서 사용하는 필드 기본 설정
		// Shutdown 같이 engine 미사용 테스트에서 불필요한 stub 경고가 발생하지 않도록 lenient() 사용
		lenient().when(orderBook.getBids()).thenReturn(new TreeMap<>(Comparator.comparing(Price::value).reversed()));
		lenient().when(orderBook.getAsks()).thenReturn(new TreeMap<>(Comparator.comparing(Price::value)));
		lenient().when(orderBook.getIndex()).thenReturn(new HashMap<>());
		lenient().when(runtimeOwner.state()).thenReturn(EngineSymbolState.ACTIVE);
		handler = new EngineHandler(SYMBOL, engine, orderBook, orderBookProjectionApplier, orderBookCachePort, engineResultPersistenceService, runtimeOwner);
	}

	private Order buyOrder(long price, long qty) {
		return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
	}

	private Order marketSellOrder(long qty) {
		return OrderFixture.createMarketSell(SYMBOL, new Quantity(qty));
	}

	private Order marketBuyQuoteQtyOrder(long quoteQty) {
		return OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(quoteQty));
	}

	private PlaceCalculationResult emptyAccepted() {
		return new PlaceCalculationResult.Accepted(SYMBOL, 1L, List.of(), List.of(), List.of());
	}

	private CancelCalculationResult cancelledResult(Order order) {
		Order cancelled = order.cancel();
		return new CancelCalculationResult.Cancelled(
			SYMBOL, order.getAcceptedSeq(),
			List.of(cancelled),
			List.of(new BookOperation.Remove(cancelled.getOrderId())));
	}

	// ── PlaceOrder ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("PlaceOrder 커맨드")
	class PlaceOrderCommand {

		@Test
		@DisplayName("Order를 engine.calculatePlace()에 전달한다")
		void handle_placeOrder_callsCalculatePlace() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(argThat(i -> i.taker().equals(order)))).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engine).calculatePlace(argThat(i -> i.taker().equals(order)));
		}

		@Test
		@DisplayName("체결 없이 처리되면 예외 없이 정상 종료한다")
		void handle_placeOrder_noTrades_doesNotThrow() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.PlaceOrder(order)));
		}

		@Test
		@DisplayName("Trade가 발생해도 예외 없이 정상 종료한다")
		void handle_placeOrder_withTrades_doesNotThrow() {
			Order order = buyOrder(10_000, 5);
			Order activated = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC,
				new Price(10_000), new Quantity(5)).activate();
			var accepted = new PlaceCalculationResult.Accepted(SYMBOL, 1L, List.of(order, activated), List.of(), List.of());
			when(engine.calculatePlace(any())).thenReturn(accepted);

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.PlaceOrder(order)));
		}

		@Test
		@DisplayName("PlaceOrder에 담긴 Order 참조가 그대로 calculatePlace에 전달된다")
		void handle_placeOrder_passesExactOrderReference() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(argThat(i -> i.taker().equals(order)))).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engine).calculatePlace(argThat(i -> i.taker().equals(order)));
			assertThat(order.getStatus().name()).isEqualTo("ACCEPTED");
		}

		@Test
		@DisplayName("calculatePlace 완료 후 orderBookCachePort.update가 orderBook을 인자로 호출된다")
		void handle_placeOrder_updatesCache() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(orderBookCachePort).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("orderBookCachePort.update는 calculatePlace 이후에 호출된다")
		void handle_placeOrder_updatesCacheAfterEngine() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			InOrder inOrder = inOrder(engine, orderBookCachePort);
			inOrder.verify(engine).calculatePlace(argThat(i -> i.taker().equals(order)));
			inOrder.verify(orderBookCachePort).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("MARKET SELL 주문도 calculatePlace()로 전달된다")
		void handle_placeOrder_marketSell_callsCalculatePlace() {
			Order order = marketSellOrder(5);
			when(engine.calculatePlace(argThat(i -> i.taker().equals(order)))).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engine).calculatePlace(argThat(i -> i.taker().equals(order)));
		}

		@Test
		@DisplayName("MARKET SELL 주문 처리 완료 후 orderBookCachePort.update가 호출된다")
		void handle_placeOrder_marketSell_updatesCache() {
			Order order = marketSellOrder(5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(orderBookCachePort).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("Accepted 결과이면 engineResultPersistenceService.persistPlaceResult()가 호출된다")
		void handle_placeOrder_accepted_delegatesToPersistenceService() {
			Order order = marketSellOrder(5);
			Order activated = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC,
				new Price(10_000), new Quantity(5)).activate();
			var accepted = new PlaceCalculationResult.Accepted(SYMBOL, 1L, List.of(activated, order), List.of(), List.of());
			when(engine.calculatePlace(any())).thenReturn(accepted);

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engineResultPersistenceService).persistPlaceResult(accepted);
		}

		@Test
		@DisplayName("BUY MARKET quoteQty 주문도 calculatePlace()로 전달된다")
		void handle_placeOrder_marketBuyQuoteQty_callsCalculatePlace() {
			Order order = marketBuyQuoteQtyOrder(50_000);
			when(engine.calculatePlace(argThat(i -> i.taker().equals(order)))).thenReturn(emptyAccepted());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engine).calculatePlace(argThat(i -> i.taker().equals(order)));
		}

		@Test
		@DisplayName("Rejected 결과이면 persistPlaceResult가 호출되지 않는다")
		void handle_placeOrder_rejected_doesNotCallPersistenceService() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any()))
				.thenReturn(new PlaceCalculationResult.Rejected(SYMBOL, 1L, PlaceRejectCode.INVALID_TIF));

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engineResultPersistenceService, never()).persistPlaceResult(any());
		}
	}

	// ── CancelOrder ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("CancelOrder 커맨드")
	class CancelOrderCommand {

		@Test
		@DisplayName("주문이 book에 없으면 예외 없이 정상 종료하고 settlement를 호출하지 않는다")
		void handle_cancelOrder_orderNotInBook_skips() {
			OrderId orderId = OrderId.newId();
			// getIndex()는 @BeforeEach에서 빈 맵으로 설정

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.CancelOrder(orderId)));

			verify(engineResultPersistenceService, never()).persistCancelResult(any());
		}

		@Test
		@DisplayName("활성 주문이면 orderBook.remove 후 persistCancelResult가 호출된다")
		void handle_cancelOrder_activeOrder_cancelsAndSettles() {
			Order activatedOrder = buyOrder(10_000, 5).activate();
			Map<OrderId, Order> index = new HashMap<>();
			index.put(activatedOrder.getOrderId(), activatedOrder);
			when(orderBook.getIndex()).thenReturn(index);
			when(engine.calculateCancel(any())).thenReturn(cancelledResult(activatedOrder));

			handler.handle(new EngineCommand.CancelOrder(activatedOrder.getOrderId()));

			verify(orderBookProjectionApplier).apply(any(), any());
			verify(engineResultPersistenceService).persistCancelResult(any(CancelCalculationResult.Cancelled.class));
		}

		@Test
		@DisplayName("활성 주문 취소 후 orderBookCachePort.update가 호출된다")
		void handle_cancelOrder_activeOrder_cacheUpdated() {
			Order activatedOrder = buyOrder(10_000, 5).activate();
			Map<OrderId, Order> index = new HashMap<>();
			index.put(activatedOrder.getOrderId(), activatedOrder);
			when(orderBook.getIndex()).thenReturn(index);
			when(engine.calculateCancel(any())).thenReturn(cancelledResult(activatedOrder));

			handler.handle(new EngineCommand.CancelOrder(activatedOrder.getOrderId()));

			verify(orderBookCachePort).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("호출 순서: persistCancelResult → orderBook.remove → orderBookCachePort.update")
		void handle_cancelOrder_callOrderIsRemoveSettlementCache() {
			Order activatedOrder = buyOrder(10_000, 5).activate();
			Map<OrderId, Order> index = new HashMap<>();
			index.put(activatedOrder.getOrderId(), activatedOrder);
			when(orderBook.getIndex()).thenReturn(index);
			when(engine.calculateCancel(any())).thenReturn(cancelledResult(activatedOrder));

			handler.handle(new EngineCommand.CancelOrder(activatedOrder.getOrderId()));

			InOrder inOrder = inOrder(engineResultPersistenceService, orderBookProjectionApplier, orderBookCachePort);
			inOrder.verify(engineResultPersistenceService).persistCancelResult(any());
			inOrder.verify(orderBookProjectionApplier).apply(any(), any());
			inOrder.verify(orderBookCachePort).update(SYMBOL, orderBook);
		}
	}

	// ── Shutdown ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("Shutdown 커맨드")
	class ShutdownCommand {

		@Test
		@DisplayName("Shutdown 커맨드를 수신해도 예외가 발생하지 않는다")
		void handle_shutdown_doesNotThrow() {
			assertDoesNotThrow(() -> handler.handle(new EngineCommand.Shutdown()));
		}

		@Test
		@DisplayName("Shutdown 커맨드를 수신하면 engine, persistenceService, cache를 호출하지 않는다")
		void handle_shutdown_noInteractions() {
			handler.handle(new EngineCommand.Shutdown());

			verifyNoInteractions(engine, engineResultPersistenceService, orderBookCachePort);
		}
	}

	// ── 실패 시나리오 ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("실패 시나리오")
	class FailureHandling {

		@Test
		@DisplayName("apply() 일반 실패 시 transitionToRebuilding()이 호출되고 예외가 전파된다")
		void applyFails_transitionsToRebuilding() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());
			doThrow(new RuntimeException("unexpected error")).when(orderBookProjectionApplier).apply(any(), any());

			assertThrows(RuntimeException.class, () -> handler.handle(new EngineCommand.PlaceOrder(order)));

			verify(runtimeOwner).transitionToRebuilding();
			verify(runtimeOwner, never()).transitionToDirty();
		}

		@Test
		@DisplayName("apply() invariant 위반 시 transitionToRebuilding()이 호출되고 예외가 전파된다")
		void applyInvariantViolation_transitionsToRebuilding() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());
			doThrow(new OrderBookInvariantViolationException("invariant violated"))
				.when(orderBookProjectionApplier).apply(any(), any());

			assertThrows(OrderBookInvariantViolationException.class,
				() -> handler.handle(new EngineCommand.PlaceOrder(order)));

			verify(runtimeOwner).transitionToRebuilding();
			verify(runtimeOwner, never()).transitionToDirty();
		}

		@Test
		@DisplayName("cache update 실패 시 상태 전이 없이 예외가 전파되지 않는다")
		void cacheUpdateFails_noStateTransition_noException() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any())).thenReturn(emptyAccepted());
			doThrow(new RuntimeException("cache error")).when(orderBookCachePort).update(any(), any());

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.PlaceOrder(order)));

			verify(runtimeOwner, never()).transitionToRebuilding();
			verify(runtimeOwner, never()).transitionToDirty();
		}

		@Test
		@DisplayName("calculate() 시스템 실패 시 transitionToDirty()가 호출되고 예외가 전파된다")
		void calculateFails_transitionsToDirty() {
			Order order = buyOrder(10_000, 5);
			doThrow(new RuntimeException("engine bug")).when(engine).calculatePlace(any());

			assertThrows(RuntimeException.class, () -> handler.handle(new EngineCommand.PlaceOrder(order)));

			verify(runtimeOwner).transitionToDirty();
			verify(runtimeOwner, never()).transitionToRebuilding();
		}

		@Test
		@DisplayName("ACTIVE가 아닌 상태에서 커맨드 수신 시 engine/persist/cache를 호출하지 않는다")
		void nonActiveState_commandDropped_noInteractions() {
			Order order = buyOrder(10_000, 5);
			when(runtimeOwner.state()).thenReturn(EngineSymbolState.REBUILDING);

			handler.handle(new EngineCommand.PlaceOrder(order));

			verifyNoInteractions(engine, engineResultPersistenceService, orderBookCachePort);
		}
	}
}
