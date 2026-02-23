package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

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

	@InjectMocks
	private EngineHandler handler;

	private Order buyOrder(long price, long qty) {
		return new Order(Side.BUY, new Price(price), new Quantity(qty));
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
			Order maker = new Order(Side.SELL, new Price(10_000), new Quantity(5));
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
	}
}
