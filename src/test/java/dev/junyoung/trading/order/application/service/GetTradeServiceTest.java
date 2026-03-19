package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.result.TradeResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.*;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetTradeService")
class GetTradeServiceTest {

    private static final Symbol SYMBOL = new Symbol("BTCUSDT");
    private static final Price PRICE = new Price(50_000L);
    private static final Quantity QTY = new Quantity(10L);

    private static final AccountId ACCOUNT_ID = OrderFixture.DEFAULT_ACCOUNT_ID;
    private static final String ACCOUNT_ID_RAW = ACCOUNT_ID.value().toString();
    private static final AccountId OTHER_ACCOUNT_ID =
            new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private GetTradeService sut;

    @Nested
    @DisplayName("getTradesByOrder()")
    class GetTradesByOrder {

        @Test
        @DisplayName("orderId가 존재하지 않으면 OrderNotFoundException이 발생한다")
        void orderNotFound_throwsOrderNotFoundException() {
            OrderId orderId = OrderId.newId();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getTradesByOrder(ACCOUNT_ID_RAW, orderId.value().toString()))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("다른 account의 주문이면 OrderNotFoundException이 발생한다")
        void otherAccountOrder_throwsOrderNotFoundException() {
            Order order = OrderFixture.createLimit(OTHER_ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QTY);
            when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> sut.getTradesByOrder(ACCOUNT_ID_RAW, order.getOrderId().value().toString()))
                    .isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        @DisplayName("정상 조회 시 Trade 리스트를 반환한다")
        void validOrder_returnsTradeList() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QTY);
            Trade trade = Trade.restore(
                    TradeId.newId(), SYMBOL,
                    order.getOrderId(), OrderId.newId(),
                    PRICE, QTY, Instant.now()
            );
            when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
            when(tradeRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(trade));

            List<TradeResult> results = sut.getTradesByOrder(ACCOUNT_ID_RAW, order.getOrderId().value().toString());

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().tradeId()).isEqualTo(trade.tradeId().value().toString());
            assertThat(results.getFirst().symbol()).isEqualTo(SYMBOL.value());
            assertThat(results.getFirst().price()).isEqualTo(PRICE.value());
            assertThat(results.getFirst().quantity()).isEqualTo(QTY.value());
        }

        @Test
        @DisplayName("buyOrderId == orderId이면 side는 BUY다")
        void buyOrderId_matchesOrderId_sideIsBuy() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QTY);
            Trade trade = Trade.restore(
                    TradeId.newId(), SYMBOL,
                    order.getOrderId(), OrderId.newId(),
                    PRICE, QTY, Instant.now()
            );
            when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
            when(tradeRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(trade));

            List<TradeResult> results = sut.getTradesByOrder(ACCOUNT_ID_RAW, order.getOrderId().value().toString());

            assertThat(results.getFirst().side()).isEqualTo("BUY");
        }

        @Test
        @DisplayName("sellOrderId == orderId이면 side는 SELL이다")
        void sellOrderId_matchesOrderId_sideIsSell() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.SELL, SYMBOL, TimeInForce.GTC, PRICE, QTY);
            Trade trade = Trade.restore(
                    TradeId.newId(), SYMBOL,
                    OrderId.newId(), order.getOrderId(),
                    PRICE, QTY, Instant.now()
            );
            when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
            when(tradeRepository.findByOrderId(order.getOrderId())).thenReturn(List.of(trade));

            List<TradeResult> results = sut.getTradesByOrder(ACCOUNT_ID_RAW, order.getOrderId().value().toString());

            assertThat(results.getFirst().side()).isEqualTo("SELL");
        }

        @Test
        @DisplayName("trade가 없는 주문은 빈 리스트를 반환한다")
        void noTrades_returnsEmptyList() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QTY);
            when(orderRepository.findById(order.getOrderId())).thenReturn(Optional.of(order));
            when(tradeRepository.findByOrderId(order.getOrderId())).thenReturn(List.of());

            List<TradeResult> results = sut.getTradesByOrder(ACCOUNT_ID_RAW, order.getOrderId().value().toString());

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTradesByAccount()")
    class GetTradesByAccount {

        @Test
        @DisplayName("trade가 있으면 AccountTradeResult를 TradeResult로 변환하여 반환한다")
        void withTrades_returnsConvertedTradeResults() {
            TradeId tradeId = TradeId.newId();
            OrderId orderId = OrderId.newId();
            AccountTradeResult accountTradeResult = new AccountTradeResult(
                    tradeId, SYMBOL, orderId, Side.BUY, PRICE, QTY, Instant.now()
            );
            when(tradeRepository.findByAccountIdWithSide(ACCOUNT_ID)).thenReturn(List.of(accountTradeResult));

            List<TradeResult> results = sut.getTradesByAccount(ACCOUNT_ID_RAW);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().tradeId()).isEqualTo(tradeId.value().toString());
            assertThat(results.getFirst().orderId()).isEqualTo(orderId.value().toString());
            assertThat(results.getFirst().side()).isEqualTo("BUY");
            assertThat(results.getFirst().price()).isEqualTo(PRICE.value());
            assertThat(results.getFirst().quantity()).isEqualTo(QTY.value());
        }

        @Test
        @DisplayName("trade가 없으면 빈 리스트를 반환한다")
        void noTrades_returnsEmptyList() {
            when(tradeRepository.findByAccountIdWithSide(ACCOUNT_ID)).thenReturn(List.of());

            List<TradeResult> results = sut.getTradesByAccount(ACCOUNT_ID_RAW);

            assertThat(results).isEmpty();
        }
    }
}
