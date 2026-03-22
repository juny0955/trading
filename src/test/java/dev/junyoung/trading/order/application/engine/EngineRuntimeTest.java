package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.junyoung.trading.order.application.exception.engine.EngineNotActiveException;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link EngineRuntime} 단위 테스트.
 *
 * <p>submit()의 state guard와 상태 전이 메서드를 직접 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EngineRuntime")
class EngineRuntimeTest {

    @Mock
    private OrderBookCachePort orderBookCachePort;

    @Mock
    private OrderBookProjectionApplier orderBookProjectionApplier;

    @Mock
    private EngineResultPersistenceService engineResultPersistenceService;

    private EngineRuntime runtime;

    private static final Symbol SYMBOL = new Symbol("BTC");

    @AfterEach
    void tearDown() {
        if (runtime != null) runtime.stop();
    }

    private EngineCommand.PlaceOrder placeOrder() {
        return new EngineCommand.PlaceOrder(
            OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5))
        );
    }

    // ── submit() state guard ─────────────────────────────────────────────────

    @Nested
    @DisplayName("submit() state guard")
    class SubmitStateGuard {

        @Test
        @DisplayName("ACTIVE 상태에서 submit()은 정상 동작한다")
        void submit_active_succeeds() {
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultPersistenceService);
            runtime.start();

            assertThat(runtime.state()).isEqualTo(EngineSymbolState.ACTIVE);
            // 예외 없이 submit되면 성공
            runtime.submit(placeOrder());
        }

        @Test
        @DisplayName("REBUILDING 상태에서 submit()은 EngineNotActiveException을 던진다")
        void submit_rebuilding_throwsEngineNotActiveException() {
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultPersistenceService);
            runtime.start();
            runtime.transitionToRebuilding();

            assertThrows(EngineNotActiveException.class, () -> runtime.submit(placeOrder()));
        }

        @Test
        @DisplayName("DIRTY 상태에서 submit()은 EngineNotActiveException을 던진다")
        void submit_dirty_throwsEngineNotActiveException() {
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultPersistenceService);
            runtime.start();
            runtime.transitionToDirty();

            assertThrows(EngineNotActiveException.class, () -> runtime.submit(placeOrder()));
        }

        @Test
        @DisplayName("HALTED 상태에서 submit()은 EngineNotActiveException을 던진다")
        void submit_halted_throwsEngineNotActiveException() {
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultPersistenceService);
            runtime.start();
            runtime.transitionToHalted();

            assertThrows(EngineNotActiveException.class, () -> runtime.submit(placeOrder()));
        }
    }
}
