package dev.junyoung.trading.order.application.service;

import java.util.Optional;
import java.util.UUID;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService")
class OrderQueryServiceTest {

    private static final Symbol SYMBOL = new Symbol("BTC");
    private static final AccountId ACCOUNT_ID =
            new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final String ACCOUNT_ID_RAW = ACCOUNT_ID.value().toString();
    private static final String OTHER_ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService sut;

    @Nested
    @DisplayName("getOrder()")
    class GetOrder {

        @Test
        @DisplayName("동일 account의 주문이면 OrderResult를 반환한다")
        void getOrder_found_returnsOrderResult() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(order.getOrderId().toString());
        }

        @Test
        @DisplayName("OrderResult 필드는 Order와 일치한다")
        void getOrder_found_resultFieldsMatchOrder() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.SELL, SYMBOL, TimeInForce.GTC, new Price(50_000), new Quantity(10));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.side()).isEqualTo("SELL");
            assertThat(result.price()).isEqualTo(50_000L);
            assertThat(result.quantity()).isEqualTo(10L);
            assertThat(result.remaining()).isEqualTo(10L);
            assertThat(result.status()).isEqualTo("ACCEPTED");
            assertThat(result.orderedAt()).isEqualTo(order.getOrderedAt());
        }

        @Test
        @DisplayName("다른 account의 주문이면 OrderNotFoundException이 발생한다")
        void getOrder_otherAccountOrder_throwsOrderNotFoundException() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            assertThrows(OrderNotFoundException.class, () -> sut.getOrder(OTHER_ACCOUNT_ID, order.getOrderId().toString()));
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 quantity는 null이다")
        void getOrder_quoteQtyMode_quantityIsNull() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_ID, Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.quantity()).isNull();
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 remaining은 0이다")
        void getOrder_quoteQtyMode_remainingIsZero() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_ID, Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.remaining()).isEqualTo(0L);
        }

        @Test
        @DisplayName("주문이 없으면 OrderNotFoundException이 발생한다")
        void getOrder_notFound_throwsOrderNotFoundException() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.getOrder(ACCOUNT_ID_RAW, orderId));
        }

        @Test
        @DisplayName("LIMIT 주문 조회 시 quantity 모드 파생 필드를 계산한다")
        void getOrder_limitMode_quantityModeFields() {
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.requestedQty()).isEqualTo(5L);
            assertThat(result.requestedQuoteQty()).isNull();
            assertThat(result.cumQuoteQty()).isNull();
            assertThat(result.leftoverQuoteQty()).isNull();
            assertThat(result.cumBaseQty()).isEqualTo(0L);
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 quote 파생 필드를 계산한다")
        void getOrder_quoteQtyMode_quoteFieldsMapped() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_ID, Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.requestedQuoteQty()).isEqualTo(50_000L);
            assertThat(result.requestedQty()).isNull();
            assertThat(result.cumQuoteQty()).isEqualTo(0L);
            assertThat(result.cumBaseQty()).isEqualTo(0L);
            assertThat(result.leftoverQuoteQty()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("quoteQty 모드의 leftoverQuoteQty는 requestedQuoteQty - cumQuoteQty다")
        void getOrder_quoteQtyMode_leftoverQuoteQtyConsistency() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_ID, Side.BUY, SYMBOL, new QuoteQty(50_000L));
            order.accumulate(30_000L, 3L);
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(ACCOUNT_ID_RAW, order.getOrderId().toString());

            assertThat(result.cumQuoteQty()).isEqualTo(30_000L);
            assertThat(result.cumBaseQty()).isEqualTo(3L);
            assertThat(result.leftoverQuoteQty()).isEqualTo(20_000L);
        }
    }
}
