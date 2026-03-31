package dev.junyoung.trading.engine.application.runtime;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.engine.application.book.OrderBookProjectionApplier;
import dev.junyoung.trading.engine.application.book.OrderBookRebuilder;
import dev.junyoung.trading.engine.application.loop.EngineCommand;
import dev.junyoung.trading.engine.application.exception.EngineNotActiveException;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import dev.junyoung.trading.engine.application.port.out.EngineResultCommitPort;
import dev.junyoung.trading.shared.domain.entity.OrderBookSnapshot;
import dev.junyoung.trading.shared.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineRuntime")
class EngineRuntimeTest {

    private static final Symbol SYMBOL = new Symbol("BTC");
    private static final AccountId ACCOUNT_ID =
        new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Mock
    private OrderBookCachePort orderBookCachePort;

    @Mock
    private OrderBookProjectionApplier orderBookProjectionApplier;

    @Mock
    private EngineResultCommitPort engineResultCommitPort;

    @Mock
    private OrderBookRebuilder orderBookRebuilder;

    @Mock
    private EngineMetrics engineMetrics;

    private EngineRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) runtime.stop();
    }

    private EngineCommand.PlaceOrder placeOrder() {
        return new EngineCommand.PlaceOrder(
            OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5)),
            Instant.now(), Instant.now()
        );
    }

    @Nested
    @DisplayName("submit() state guard")
    class SubmitStateGuard {

        @Test
        @DisplayName("ACTIVE 상태에서는 submit()이 허용된다")
        void submit_active_succeeds() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of());
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();

            assertThat(runtime.state()).isEqualTo(EngineSymbolStatus.ACTIVE);
            runtime.submit(placeOrder());
        }

        @Test
        @DisplayName("REBUILDING 상태에서는 submit()이 EngineNotActiveException을 던진다")
        void submit_rebuilding_throwsEngineNotActiveException() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of());
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();
            runtime.transitionToRebuilding();

            assertThrows(EngineNotActiveException.class, () -> runtime.submit(placeOrder()));
        }

        @Test
        @DisplayName("DIRTY 상태에서는 submit()이 EngineNotActiveException을 던진다")
        void submit_dirty_throwsEngineNotActiveException() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of());
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();
            runtime.transitionToDirty();

            assertThrows(EngineNotActiveException.class, () -> runtime.submit(placeOrder()));
        }
    }

    @Nested
    @DisplayName("attemptRebuild()")
    class AttemptRebuild {

        @Test
        @DisplayName("생성 시 open order replay 결과가 오더북에 반영된다")
        void constructor_replaysOpenOrdersIntoOrderBook() {
            Order bestBid = activeLimitOrder("bid-1", 1L, Side.BUY, 10_100L, 3L);
            Order bestAsk = activeLimitOrder("ask-1", 2L, Side.SELL, 10_200L, 4L);
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of(bestBid, bestAsk));

            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);

            ArgumentCaptor<OrderBookSnapshot> captor = ArgumentCaptor.forClass(OrderBookSnapshot.class);
            verify(orderBookCachePort).update(eq(SYMBOL), captor.capture());

            OrderBookSnapshot snapshot = captor.getValue();
            assertThat(snapshot.bids()).containsKey(new Price(10_100L));
            assertThat(snapshot.asks()).containsKey(new Price(10_200L));
        }

        @Test
        @DisplayName("동일 가격 replay 주문은 같은 가격 레벨 수량으로 집계된다")
        void constructor_replaysSamePriceOrdersIntoSamePriceLevel() {
            Order first = activeLimitOrder("bid-1", 10L, Side.BUY, 10_100L, 3L);
            Order second = activeLimitOrder("bid-2", 20L, Side.BUY, 10_100L, 5L);
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of(first, second));

            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);

            ArgumentCaptor<OrderBookSnapshot> captor = ArgumentCaptor.forClass(OrderBookSnapshot.class);
            verify(orderBookCachePort).update(eq(SYMBOL), captor.capture());

            OrderBookSnapshot snapshot = captor.getValue();
            assertThat(snapshot.bids()).containsEntry(new Price(10_100L), new Quantity(8L));
            assertThat(snapshot.asks()).isEmpty();
        }

        @Test
        @DisplayName("rebuild 성공 시 ACTIVE 상태로 전환된다")
        void attemptRebuild_success_transitionsToActive() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of());
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();
            runtime.transitionToRebuilding();
            assertThat(runtime.state()).isEqualTo(EngineSymbolStatus.REBUILDING);

            runtime.attemptRebuild();

            assertThat(runtime.state()).isEqualTo(EngineSymbolStatus.ACTIVE);
        }

        @Test
        @DisplayName("loadOpenOrders 실패 시 DIRTY 상태로 전환된다")
        void attemptRebuild_loadFails_transitionsToDirty() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenThrow(new RuntimeException("DB error"));
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();
            runtime.transitionToRebuilding();

            runtime.attemptRebuild();

            assertThat(runtime.state()).isEqualTo(EngineSymbolStatus.DIRTY);
        }

        @Test
        @DisplayName("rebuild 성공 시 캐시가 갱신된다")
        void attemptRebuild_success_updatesCache() {
            when(orderBookRebuilder.loadOpenOrders(SYMBOL)).thenReturn(List.of());
            runtime = new EngineRuntime(SYMBOL, orderBookCachePort, orderBookProjectionApplier, engineResultCommitPort, orderBookRebuilder, engineMetrics, 0L);
            runtime.start();
            runtime.transitionToRebuilding();
            clearInvocations(orderBookCachePort);

            runtime.attemptRebuild();

            verify(orderBookCachePort, times(1)).update(eq(SYMBOL), any(OrderBookSnapshot.class));
        }
    }

    private Order activeLimitOrder(String clientOrderId, long acceptedSeq, Side side, long price, long quantity) {
        return Order.create(
            OrderId.newId(),
            ACCOUNT_ID,
            clientOrderId,
            acceptedSeq,
            SYMBOL,
            side,
            OrderType.LIMIT,
            TimeInForce.GTC,
            new Price(price),
            null,
            new Quantity(quantity)
        ).activate();
    }
}
