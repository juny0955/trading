package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
	private OrderBookCache orderBookCache;

	@Mock
	private OrderRepository orderRepository;

	private EngineHandler handler;

	private static final Symbol SYMBOL = new Symbol("BTC");

	@BeforeEach
	void setUp() {
		handler = new EngineHandler(SYMBOL, engine, orderBook, orderBookCache, orderRepository);
	}

	private Order buyOrder(long price, long qty) {
		return Order.createLimit(Side.BUY, SYMBOL, new Price(price), new Quantity(qty));
	}

	// ── PlaceOrder ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("PlaceOrder 커맨드")
	class PlaceOrderCommand {

		@Test
		@DisplayName("Order를 MatchingEngine.placeLimitOrder()에 전달한다")
		void handle_placeOrder_callsPlaceLimitOrder() {
			Order order = buyOrder(10_000, 5);
			when(engine.placeLimitOrder(order)).thenReturn(List.of());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(engine).placeLimitOrder(order);
		}

		@Test
		@DisplayName("체결 없이 처리되면 예외 없이 정상 종료한다")
		void handle_placeOrder_noTrades_doesNotThrow() {
			Order order = buyOrder(10_000, 5);
			when(engine.placeLimitOrder(order)).thenReturn(List.of());

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.PlaceOrder(order)));
		}

		@Test
		@DisplayName("Trade가 발생해도 예외 없이 정상 종료한다")
		void handle_placeOrder_withTrades_doesNotThrow() {
			Order taker = buyOrder(10_000, 5);
			Order maker = Order.createLimit(Side.SELL, SYMBOL, new Price(10_000), new Quantity(5));
			maker.activate();
			Trade trade = Trade.of(taker, maker, new Quantity(5));
			when(engine.placeLimitOrder(taker)).thenReturn(List.of(trade));

			assertDoesNotThrow(() -> handler.handle(new EngineCommand.PlaceOrder(taker)));
		}

		@Test
		@DisplayName("PlaceOrder에 담긴 Order 참조가 그대로 placeLimitOrder에 전달된다")
		void handle_placeOrder_passesExactOrderReference() {
			Order order = buyOrder(10_000, 5);
			when(engine.placeLimitOrder(order)).thenReturn(List.of());

			handler.handle(new EngineCommand.PlaceOrder(order));

			// same reference — orderId 포함 모든 필드가 동일한 객체가 전달됨을 보장
			verify(engine).placeLimitOrder(order);
			assertThat(order.getStatus().name()).isEqualTo("ACCEPTED"); // 핸들러는 상태를 바꾸지 않음
		}

		@Test
		@DisplayName("placeLimitOrder 완료 후 orderBookCache.update가 orderBook을 인자로 호출된다")
		void handle_placeOrder_updatesCache() {
			Order order = buyOrder(10_000, 5);
			when(engine.placeLimitOrder(order)).thenReturn(List.of());

			handler.handle(new EngineCommand.PlaceOrder(order));

			verify(orderBookCache).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("orderBookCache.update는 placeLimitOrder 이후에 호출된다")
		void handle_placeOrder_updatesCacheAfterEngine() {
			Order order = buyOrder(10_000, 5);
			when(engine.placeLimitOrder(order)).thenReturn(List.of());

			handler.handle(new EngineCommand.PlaceOrder(order));

			InOrder inOrder = inOrder(engine, orderBookCache);
			inOrder.verify(engine).placeLimitOrder(order);
			inOrder.verify(orderBookCache).update(SYMBOL, orderBook);
		}
	}

	// ── CancelOrder ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("CancelOrder 커맨드")
	class CancelOrderCommand {

		@Test
		@DisplayName("OrderId를 MatchingEngine.cancelOrder()에 전달한다")
		void handle_cancelOrder_callsCancelOrder() {
			OrderId orderId = OrderId.newId();

			handler.handle(new EngineCommand.CancelOrder(orderId));

			verify(engine).cancelOrder(orderId);
		}

		@Test
		@DisplayName("엔진이 IllegalStateException을 던지면 그대로 전파된다")
		void handle_cancelOrder_propagatesIllegalStateException() {
			OrderId orderId = OrderId.newId();
			doThrow(new IllegalStateException("Already Processed")).when(engine).cancelOrder(orderId);

			assertThrows(IllegalStateException.class, () -> handler.handle(new EngineCommand.CancelOrder(orderId)));
		}

		@Test
		@DisplayName("cancelOrder 완료 후 orderBookCache.update가 orderBook을 인자로 호출된다")
		void handle_cancelOrder_updatesCache() {
			OrderId orderId = OrderId.newId();

			handler.handle(new EngineCommand.CancelOrder(orderId));

			verify(orderBookCache).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("orderBookCache.update는 cancelOrder 이후에 호출된다")
		void handle_cancelOrder_updatesCacheAfterEngine() {
			OrderId orderId = OrderId.newId();

			handler.handle(new EngineCommand.CancelOrder(orderId));

			InOrder inOrder = inOrder(engine, orderBookCache);
			inOrder.verify(engine).cancelOrder(orderId);
			inOrder.verify(orderBookCache).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("엔진이 예외를 던지면 orderBookCache.update는 호출되지 않는다")
		void handle_cancelOrder_engineThrows_doesNotUpdateCache() {
			OrderId orderId = OrderId.newId();
			doThrow(new IllegalStateException("Already Processed")).when(engine).cancelOrder(orderId);

			assertThrows(IllegalStateException.class, () -> handler.handle(new EngineCommand.CancelOrder(orderId)));

			verify(orderBookCache, never()).update(any(), any());
		}

		@Test
		@DisplayName("engine.cancelOrder()가 반환한 Order를 orderRepository.save()에 전달한다")
		void handle_cancelOrder_savesReturnedOrderToRepository() {
			OrderId orderId = OrderId.newId();
			Order cancelled = buyOrder(10_000, 5);
			when(engine.cancelOrder(orderId)).thenReturn(cancelled);

			handler.handle(new EngineCommand.CancelOrder(orderId));

			verify(orderRepository).save(cancelled);
		}

		@Test
		@DisplayName("호출 순서: engine.cancelOrder → orderRepository.save → orderBookCache.update")
		void handle_cancelOrder_callOrderIsEngineRepositoryCache() {
			OrderId orderId = OrderId.newId();
			Order cancelled = buyOrder(10_000, 5);
			when(engine.cancelOrder(orderId)).thenReturn(cancelled);

			handler.handle(new EngineCommand.CancelOrder(orderId));

			InOrder inOrder = inOrder(engine, orderRepository, orderBookCache);
			inOrder.verify(engine).cancelOrder(orderId);
			inOrder.verify(orderRepository).save(cancelled);
			inOrder.verify(orderBookCache).update(SYMBOL, orderBook);
		}

		@Test
		@DisplayName("엔진이 예외를 던지면 orderRepository.save는 호출되지 않는다")
		void handle_cancelOrder_engineThrows_doesNotSaveToRepository() {
			OrderId orderId = OrderId.newId();
			doThrow(new IllegalStateException("Already Processed")).when(engine).cancelOrder(orderId);

			assertThrows(IllegalStateException.class, () -> handler.handle(new EngineCommand.CancelOrder(orderId)));

			verify(orderRepository, never()).save(any());
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
		@DisplayName("Shutdown 커맨드를 수신하면 engine, repository, cache를 호출하지 않는다")
		void handle_shutdown_noInteractions() {
			handler.handle(new EngineCommand.Shutdown());

			verifyNoInteractions(engine, orderRepository, orderBookCache);
		}
	}
}
