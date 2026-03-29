package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.shared.domain.value.Asset;
import dev.junyoung.trading.engine.application.metrics.ReplayMetrics;
import dev.junyoung.trading.engine.application.service.EngineStartupRecoveryService;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.QuoteQty;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineStartupRecoveryService")
class EngineStartupRecoveryServiceTest {

    private static final Symbol SYMBOL = new Symbol("BTCKRW");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldReservationPort holdReservationPort;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private ReplayMetrics replayMetrics;

    @InjectMocks
    private EngineStartupRecoveryService sut;

    @Nested
    @DisplayName("cleanupOrphanAccepted()")
    class CleanupOrphanAccepted {

        @Test
        @DisplayName("orphan이 없으면 save, release, delete가 호출되지 않는다")
        void noOrphans_nothingCalled() {
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of());

            sut.cleanupOrphanAccepted(SYMBOL);

            verify(orderRepository, never()).save(any());
            verifyNoInteractions(holdReservationPort, idempotencyKeyRepository);
        }

        @Test
        @DisplayName("LIMIT BUY orphan — CANCELLED 저장, KRW hold 해제, idempotency key 삭제")
        void limitBuy_cancelsAndReleasesKrwHold() {
            // price=10_000, qty=10 → hold = 100_000 KRW
            Order orphan = OrderFixture.createLimitBuy(SYMBOL);
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of(orphan));

            sut.cleanupOrphanAccepted(SYMBOL);

            ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(savedOrder.capture());
            assertThat(savedOrder.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("KRW")),
                    eq(100_000L)
            );
            verify(idempotencyKeyRepository).delete(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(OrderFixture.DEFAULT_CLIENT_ORDER_ID)
            );
        }

        @Test
        @DisplayName("LIMIT SELL orphan — 심볼 자산(BTCKRW) hold 해제")
        void limitSell_releasesSymbolAssetHold() {
            // qty=5 → hold = 5 BTCKRW
            Order orphan = OrderFixture.createLimit(
                    Side.SELL, SYMBOL, TimeInForce.GTC, new Price(10_000L), new Quantity(5)
            );
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of(orphan));

            sut.cleanupOrphanAccepted(SYMBOL);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("BTCKRW")),
                    eq(5L)
            );
        }

        @Test
        @DisplayName("MARKET BUY orphan — quoteQty만큼 KRW hold 해제")
        void marketBuy_releasesKrwWithQuoteQty() {
            Order orphan = OrderFixture.createMarketBuyWithQuoteQty(
                    Side.BUY, SYMBOL, new QuoteQty(50_000)
            );
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of(orphan));

            sut.cleanupOrphanAccepted(SYMBOL);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("KRW")),
                    eq(50_000L)
            );
        }

        @Test
        @DisplayName("MARKET SELL orphan — 심볼 자산 hold 해제")
        void marketSell_releasesSymbolAssetHold() {
            Order orphan = OrderFixture.createMarketSell(SYMBOL, new Quantity(7));
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of(orphan));

            sut.cleanupOrphanAccepted(SYMBOL);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("BTCKRW")),
                    eq(7L)
            );
        }

        @Test
        @DisplayName("orphan이 둘이면 각각 한 번씩 처리된다")
        void twoOrphans_bothProcessed() {
            Order o1 = OrderFixture.createLimitBuy(SYMBOL);
            Order o2 = OrderFixture.createLimitBuy(SYMBOL);
            when(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).thenReturn(List.of(o1, o2));

            sut.cleanupOrphanAccepted(SYMBOL);

            verify(orderRepository, times(2)).save(any());
            verify(holdReservationPort, times(2)).release(any(), any(), anyLong());
            verify(idempotencyKeyRepository, times(2)).delete(any(), any());
        }
    }
}
