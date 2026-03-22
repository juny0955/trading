package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import dev.junyoung.trading.order.application.service.SettlementService;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.service.MatchingEngineTest;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;
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
	private OrderBookCachePort orderBookCachePort;

	@Mock
	private SettlementService settlementService;

	private EngineHandler handler;

	private static final Symbol SYMBOL = new Symbol("BTC");

	@BeforeEach
	void setUp() {
		// OrderBookViewFactory.create(orderBook) 에서 사용하는 필드 기본 설정
		// Shutdown 같이 engine 미사용 테스트에서 불필요한 stub 경고가 발생하지 않도록 lenient() 사용
		lenient().when(orderBook.getBids()).thenReturn(new TreeMap<>(Comparator.comparing(Price::value).reversed()));
		lenient().when(orderBook.getAsks()).thenReturn(new TreeMap<>(Comparator.comparing(Price::value)));
		lenient().when(orderBook.getIndex()).thenReturn(new HashMap<>());
		handler = new EngineHandler(SYMBOL, engine, orderBook, orderBookCachePort, settlementService);
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
		@DisplayName("MARKET SELL 주문 처리 후 PlaceResult를 settlementService.settlement()에 전달한다")
		void handle_placeOrder_marketSell_delegatesToSettlementService() {
			Order order = marketSellOrder(5);
			Order activated = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC,
				new Price(10_000), new Quantity(5)).activate();
			var accepted = new PlaceCalculationResult.Accepted(SYMBOL, 1L, List.of(activated, order), List.of(), List.of());
			when(engine.calculatePlace(any())).thenReturn(accepted);

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(settlementService).settlement(PlaceResult.of(accepted.updatedOrders(), accepted.trades()));
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
		@DisplayName("Rejected 결과이면 빈 PlaceResult로 settlement가 호출된다")
		void handle_placeOrder_rejected_delegatesEmptyResultToSettlementService() {
			Order order = buyOrder(10_000, 5);
			when(engine.calculatePlace(any()))
				.thenReturn(new PlaceCalculationResult.Rejected(SYMBOL, 1L, PlaceRejectCode.INVALID_TIF));

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(settlementService).settlement(PlaceResult.of(List.of(), List.of()));
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

			verify(settlementService, never()).cancelSettlement(any());
		}

		@Test
		@DisplayName("주문이 없어도 orderBookCachePort.update는 호출된다")
		void handle_cancelOrder_orderNotInBook_cacheStillUpdated() {
			OrderId orderId = OrderId.newId();

			handler.handle(new EngineCommand.CancelOrder(orderId));

			verify(orderBookCachePort).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("활성 주문이면 orderBook.remove → order.cancel() → settlementService.cancelSettlement 순으로 호출된다")
		void handle_cancelOrder_activeOrder_cancelsAndSettles() {
			Order activatedOrder = buyOrder(10_000, 5).activate();
			Map<OrderId, Order> index = new HashMap<>();
			index.put(activatedOrder.getOrderId(), activatedOrder);
			when(orderBook.getIndex()).thenReturn(index);
			when(engine.calculateCancel(any())).thenReturn(cancelledResult(activatedOrder));

			handler.handle(new EngineCommand.CancelOrder(activatedOrder.getOrderId()));

			verify(orderBook).remove(activatedOrder.getOrderId());
			verify(settlementService).cancelSettlement(argThat(o ->
				o.getOrderId().equals(activatedOrder.getOrderId())
					&& o.getStatus() == OrderStatus.CANCELLED));
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
		@DisplayName("호출 순서: orderBook.remove → settlementService.cancelSettlement → orderBookCachePort.update")
		void handle_cancelOrder_callOrderIsRemoveSettlementCache() {
			Order activatedOrder = buyOrder(10_000, 5).activate();
			Map<OrderId, Order> index = new HashMap<>();
			index.put(activatedOrder.getOrderId(), activatedOrder);
			when(orderBook.getIndex()).thenReturn(index);
			when(engine.calculateCancel(any())).thenReturn(cancelledResult(activatedOrder));

			handler.handle(new EngineCommand.CancelOrder(activatedOrder.getOrderId()));

			InOrder inOrder = inOrder(orderBook, settlementService, orderBookCachePort);
			inOrder.verify(orderBook).remove(activatedOrder.getOrderId());
			inOrder.verify(settlementService).cancelSettlement(any());
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
		@DisplayName("Shutdown 커맨드를 수신하면 engine, settlementService, cache를 호출하지 않는다")
		void handle_shutdown_noInteractions() {
			handler.handle(new EngineCommand.Shutdown());

			verifyNoInteractions(engine, settlementService, orderBookCachePort);
		}
	}
}
