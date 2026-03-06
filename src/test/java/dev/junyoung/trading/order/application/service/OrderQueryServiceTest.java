package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.fixture.OrderFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.order.application.exception.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService")
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderQueryService sut;

    @Nested
    @DisplayName("getOrder()")
    class GetOrder {

        private static final Symbol SYMBOL = new Symbol("BTC");

        @Test
        @DisplayName("주문이 존재하면 OrderResult를 반환한다")
        void getOrder_found_returnsOrderResult() {
            Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(order.getOrderId().toString());
        }

        @Test
        @DisplayName("OrderResult의 side/price/quantity/remaining/status/orderedAt이 Order와 일치한다")
        void getOrder_found_resultFieldsMatchOrder() {
            Order order = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(50_000), new Quantity(10));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.side()).isEqualTo("SELL");
            assertThat(result.price()).isEqualTo(50_000L);
            assertThat(result.quantity()).isEqualTo(10L);
            assertThat(result.remaining()).isEqualTo(10L);
            assertThat(result.status()).isEqualTo("ACCEPTED");
            assertThat(result.orderedAt()).isEqualTo(order.getOrderedAt());
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 quantity가 null로 반환된다")
        void getOrder_quoteQtyMode_quantityIsNull() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.quantity()).isNull();
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 remaining이 0으로 반환된다")
        void getOrder_quoteQtyMode_remainingIsZero() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.remaining()).isEqualTo(0L);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 OrderNotFoundException이 발생한다")
        void getOrder_notFound_throwsOrderNotFoundException() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.getOrder(orderId));
        }

        @Test
        @DisplayName("LIMIT 주문 조회 시 requestedQty=quantity, requestedQuoteQty/cumQuoteQty/leftoverQuoteQty=null")
        void getOrder_limitMode_quantityModeFields() {
            Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.requestedQty()).isEqualTo(5L);
            assertThat(result.requestedQuoteQty()).isNull();
            assertThat(result.cumQuoteQty()).isNull();
            assertThat(result.leftoverQuoteQty()).isNull();
            assertThat(result.cumBaseQty()).isEqualTo(0L); // quantity - remaining = 5 - 5 = 0
        }

        @Test
        @DisplayName("quoteQty 모드 주문 조회 시 requestedQuoteQty/cumQuoteQty/leftoverQuoteQty가 올바르게 매핑된다")
        void getOrder_quoteQtyMode_quoteFieldsMapped() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(50_000L));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.requestedQuoteQty()).isEqualTo(50_000L);
            assertThat(result.requestedQty()).isNull();
            assertThat(result.cumQuoteQty()).isEqualTo(0L);
            assertThat(result.cumBaseQty()).isEqualTo(0L);
            assertThat(result.leftoverQuoteQty()).isEqualTo(50_000L); // 50_000 - 0
        }

        @Test
        @DisplayName("quoteQty 모드: leftoverQuoteQty = requestedQuoteQty - cumQuoteQty")
        void getOrder_quoteQtyMode_leftoverQuoteQtyConsistency() {
            Order order = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(50_000L));
            order.accumulate(30_000L, 3L);
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result.cumQuoteQty()).isEqualTo(30_000L);
            assertThat(result.cumBaseQty()).isEqualTo(3L);
            assertThat(result.leftoverQuoteQty()).isEqualTo(20_000L); // 50_000 - 30_000
        }
    }
}
