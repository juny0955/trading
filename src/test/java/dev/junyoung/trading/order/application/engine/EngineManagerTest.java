package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.config.TradingProperties;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link EngineManager} 단위 테스트.
 *
 * <p>{@link TradingProperties}와 {@link OrderRepository}를 mock으로 대체한다.
 * {@link EngineContext}는 내부에서 직접 생성하므로 실제 engine-thread가 기동된다.
 * 각 테스트는 {@code @AfterEach}에서 {@link EngineManager#stop()}으로 스레드를 정리한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineManager")
class EngineManagerTest {

	@Mock
	private TradingProperties tradingProperties;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderBookCache orderBookCache;

	private EngineManager engineManager;

	@AfterEach
	void tearDown() {
		if (engineManager != null) {
			engineManager.stop();
		}
	}

	private EngineCommand.PlaceOrder placeOrder(String symbol) {
		Symbol sym = new Symbol(symbol);
		Order order = Order.createLimit(Side.BUY, sym, new Price(10_000), new Quantity(5));
		return new EngineCommand.PlaceOrder(order);
	}

	// ── start() ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("start()")
	class Start {

		@Test
		@DisplayName("symbols가 비어 있으면 예외 없이 완료된다")
		void start_emptySymbols_doesNotThrow() {
			when(tradingProperties.getSymbols()).thenReturn(List.of());
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);

			assertDoesNotThrow(() -> engineManager.start());
		}

		@Test
		@DisplayName("단일 심볼로 시작하면 예외가 발생하지 않는다")
		void start_singleSymbol_doesNotThrow() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);

			assertDoesNotThrow(() -> engineManager.start());
		}

		@Test
		@DisplayName("복수 심볼로 시작하면 예외가 발생하지 않는다")
		void start_multipleSymbols_doesNotThrow() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH", "SOL"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);

			assertDoesNotThrow(() -> engineManager.start());
		}
	}

	// ── submit() ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("submit()")
	class Submit {

		@BeforeEach
		void setUp() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);
			engineManager.start();
		}

		@Test
		@DisplayName("등록된 심볼로 커맨드를 제출하면 예외가 발생하지 않는다")
		void submit_knownSymbol_doesNotThrow() {
			assertDoesNotThrow(() -> engineManager.submit(new Symbol("BTC"), placeOrder("BTC")));
		}

		@Test
		@DisplayName("미등록 심볼로 제출하면 IllegalArgumentException이 발생한다")
		void submit_unknownSymbol_throwsIllegalArgumentException() {
			assertThrows(IllegalArgumentException.class,
					() -> engineManager.submit(new Symbol("XRP"), placeOrder("XRP")));
		}

		@Test
		@DisplayName("BTC·ETH 심볼이 각자 독립적으로 커맨드를 수신한다")
		void submit_differentSymbols_bothAcceptCommands() {
			assertDoesNotThrow(() -> {
				engineManager.submit(new Symbol("BTC"), placeOrder("BTC"));
				engineManager.submit(new Symbol("ETH"), placeOrder("ETH"));
			});
		}

		@Test
		@DisplayName("stop() 후 submit()을 호출하면 IllegalStateException이 발생한다")
		void submit_afterStop_throwsIllegalStateException() {
			engineManager.stop();

			assertThrows(IllegalStateException.class,
					() -> engineManager.submit(new Symbol("BTC"), placeOrder("BTC")));
		}
	}

	// ── stop() ───────────────────────────────────────────────────────────

	@Nested
	@DisplayName("stop()")
	class Stop {

		@Test
		@DisplayName("심볼 없이 시작한 뒤 stop()은 예외 없이 완료된다")
		void stop_noSymbols_doesNotThrow() {
			when(tradingProperties.getSymbols()).thenReturn(List.of());
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);
			engineManager.start();

			assertDoesNotThrow(() -> engineManager.stop());
		}

		@Test
		@DisplayName("단일 심볼 엔진을 정상 종료한다")
		void stop_singleSymbol_terminatesGracefully() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);
			engineManager.start();

			assertDoesNotThrow(() -> engineManager.stop());
		}

		@Test
		@DisplayName("복수 심볼의 모든 엔진을 정상 종료한다")
		void stop_multipleSymbols_allTerminateGracefully() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH", "SOL"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);
			engineManager.start();

			assertDoesNotThrow(() -> engineManager.stop());
		}

		@Test
		@DisplayName("stop()을 여러 번 호출해도 예외가 발생하지 않는다")
		void stop_calledMultipleTimes_doesNotThrow() {
			when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
			engineManager = new EngineManager(tradingProperties, orderRepository, orderBookCache);
			engineManager.start();

			assertDoesNotThrow(() -> {
				engineManager.stop();
				engineManager.stop();
			});
		}
	}
}
