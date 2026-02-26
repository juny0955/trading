package dev.junyoung.trading.order.application.service;

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

import dev.junyoung.trading.order.application.port.in.result.OrderResult;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
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
            Order order = Order.createLimit(Side.BUY, SYMBOL, new Price(10_000), new Quantity(5));
            when(orderRepository.findById(order.getOrderId().toString()))
                    .thenReturn(Optional.of(order));

            OrderResult result = sut.getOrder(order.getOrderId().toString());

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(order.getOrderId().toString());
        }

        @Test
        @DisplayName("OrderResult의 side/price/quantity/remaining/status/orderedAt이 Order와 일치한다")
        void getOrder_found_resultFieldsMatchOrder() {
            Order order = Order.createLimit(Side.SELL, SYMBOL, new Price(50_000), new Quantity(10));
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
        @DisplayName("주문이 존재하지 않으면 IllegalArgumentException이 발생한다")
        void getOrder_notFound_throwsIllegalArgumentException() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> sut.getOrder(orderId));
        }

        @Test
        @DisplayName("주문이 존재하지 않을 때 예외 메시지가 'Order Not Found'이다")
        void getOrder_notFound_exceptionMessageIsOrderNotFound() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> sut.getOrder(orderId));

            assertThat(ex.getMessage()).isEqualTo("Order Not Found");
        }
    }
}
