package dev.junyoung.trading.order.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompensationService")
class OrderCompensationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldReservationPort holdReservationPort;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @InjectMocks
    private OrderCompensationService sut;

    @Nested
    @DisplayName("compensate()")
    class Compensate {

        @Test
        @DisplayName("LIMIT BUY — 주문 삭제, KRW hold 해제, 멱등성 키 삭제가 모두 호출된다")
        void compensate_limitBuy_callsAllPorts() {
            Order order = OrderFixture.createLimit(
                    Side.BUY,
                    new Symbol("BTC"),
                    TimeInForce.GTC,
                    new Price(10_000L),
                    new Quantity(3)
            );

            sut.compensate(order);

            verify(orderRepository).deleteById(order.getOrderId());
            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("KRW")),
                    eq(30_000L)
            );
            verify(idempotencyKeyRepository).delete(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(OrderFixture.DEFAULT_CLIENT_ORDER_ID)
            );
        }

        @Test
        @DisplayName("LIMIT SELL — hold release가 심볼 자산(BTC)으로 호출된다")
        void compensate_limitSell_releasesSymbolAsset() {
            Order order = OrderFixture.createLimit(
                    Side.SELL,
                    new Symbol("BTC"),
                    TimeInForce.GTC,
                    new Price(10_000L),
                    new Quantity(5)
            );

            sut.compensate(order);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("BTC")),
                    eq(5L)
            );
        }

        @Test
        @DisplayName("MARKET BUY — hold release가 quoteQty만큼 KRW로 호출된다")
        void compensate_marketBuy_releasesKrwWithQuoteQty() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(
                    Side.BUY,
                    new Symbol("BTC"),
                    new QuoteQty(50_000)
            );

            sut.compensate(order);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("KRW")),
                    eq(50_000L)
            );
        }

        @Test
        @DisplayName("MARKET SELL — hold release가 심볼 자산으로 호출된다")
        void compensate_marketSell_releasesSymbolAsset() {
            Order order = OrderFixture.createMarketSell(
                    new Symbol("BTC"),
                    new Quantity(7)
            );

            sut.compensate(order);

            verify(holdReservationPort).release(
                    eq(OrderFixture.DEFAULT_ACCOUNT_ID),
                    eq(new Asset("BTC")),
                    eq(7L)
            );
        }
    }
}
