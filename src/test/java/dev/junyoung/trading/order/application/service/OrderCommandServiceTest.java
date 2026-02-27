package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandService")
class OrderCommandServiceTest {

    @Mock
    private EngineManager engineManager;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderCommandService sut;

    private Order buyOrder(String symbol) {
        return Order.createLimit(Side.BUY, new Symbol(symbol), new Price(10_000), new Quantity(5));
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test
        @DisplayName("orderId를 UUID 문자열로 반환한다")
        void placeOrder_returnsUuidString() {
            String result = sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("PlaceOrder 커맨드를 EngineManager에 제출한다")
        void placeOrder_submitsPlaceOrderCommand() {
            sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.PlaceOrder.class);
        }

        @Test
        @DisplayName("커맨드에 담긴 Order의 side/price/quantity가 입력값과 일치한다")
        void placeOrder_commandContainsCorrectOrderFields() {
            sut.placeOrder("BTC", "SELL", "LIMIT", 20_000L, 3);

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(cmd.order().getSide().name()).isEqualTo("SELL");
            assertThat(cmd.order().getLimitPriceOrThrow().value()).isEqualTo(20_000L);
            assertThat(cmd.order().getQuantity().value()).isEqualTo(3L);
        }

        @Test
        @DisplayName("반환된 orderId가 커맨드의 Order orderId와 동일하다")
        void placeOrder_returnedOrderIdMatchesCommandOrderId() {
            String returnedId = sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(returnedId).isEqualTo(cmd.order().getOrderId().toString());
        }

        @Test
        @DisplayName("잘못된 side 값이 전달되면 IllegalArgumentException이 발생한다")
        void placeOrder_invalidSide_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.placeOrder("BTC", "INVALID", "LIMIT", 10_000L, 5));
        }

        @Test
        @DisplayName("생성된 Order를 ACCEPTED 상태로 orderRepository에 저장한다")
        void placeOrder_savesOrderToRepository() {
            sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus().name()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("orderRepository에 저장되는 Order와 EngineManager에 제출되는 Order가 동일 객체다")
        void placeOrder_savedOrderIsSameAsSubmittedOrder() {
            sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            ArgumentCaptor<Order> repositoryCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<EngineCommand> engineCaptor = forClass(EngineCommand.class);
            verify(orderRepository).save(repositoryCaptor.capture());
            verify(engineManager).submit(any(Symbol.class), engineCaptor.capture());

            Order savedOrder = repositoryCaptor.getValue();
            Order submittedOrder = ((EngineCommand.PlaceOrder) engineCaptor.getValue()).order();
            assertThat(savedOrder).isSameAs(submittedOrder);
        }

        @Test
        @DisplayName("orderRepository.save는 engineManager.submit 이전에 호출된다")
        void placeOrder_savesOrderBeforeSubmittingCommand() {
            sut.placeOrder("BTC", "BUY", "LIMIT", 10_000L, 5);

            InOrder inOrder = inOrder(orderRepository, engineManager);
            inOrder.verify(orderRepository).save(any());
            inOrder.verify(engineManager).submit(any(), any());
        }
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("CancelOrder 커맨드를 EngineManager에 제출한다")
        void cancelOrder_submitsCancelOrderCommand() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(orderId);

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.CancelOrder.class);
        }

        @Test
        @DisplayName("커맨드에 담긴 OrderId가 입력값과 일치한다")
        void cancelOrder_commandContainsCorrectOrderId() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(orderId);

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.CancelOrder cmd = (EngineCommand.CancelOrder) captor.getValue();
            assertThat(cmd.orderId().toString()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("orderRepository에 주문이 없으면 IllegalArgumentException이 발생한다")
        void cancelOrder_orderNotFound_throwsIllegalArgumentException() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> sut.cancelOrder(orderId));
        }

        @Test
        @DisplayName("orderRepository에 주문이 없을 때 예외 메시지가 'Order Not Found'이다")
        void cancelOrder_orderNotFound_exceptionMessageIsOrderNotFound() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> sut.cancelOrder(orderId));
            assertThat(ex.getMessage()).isEqualTo("Order Not Found");
        }

        @Test
        @DisplayName("cancelOrder는 orderRepository.save()를 호출하지 않는다 (취소 저장은 EngineHandler 담당)")
        void cancelOrder_doesNotSaveToRepository() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(orderId);

            verify(orderRepository, never()).save(any());
        }
    }
}
