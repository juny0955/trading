package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.fixture.OrderFixture;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.exception.order.OrderAlreadyFinalizedException;
import dev.junyoung.trading.order.application.exception.order.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
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
        return OrderFixture.createLimit(Side.BUY, new Symbol(symbol), TimeInForce.GTC, new Price(10_000), new Quantity(5));
    }

    private PlaceOrderCommand limitCommand(String symbol, String side, Long price, int quantity) {
        return new PlaceOrderCommand(
                new Symbol(symbol),
                Side.valueOf(side),
                OrderType.LIMIT,
                null,
                price == null ? null : new Price(price),
                null,
                new Quantity(quantity),
                null
        );
    }

    private PlaceOrderCommand limitCommand(String symbol, String side, Long price, int quantity, String clientOrderId) {
        return new PlaceOrderCommand(
                new Symbol(symbol),
                Side.valueOf(side),
                OrderType.LIMIT,
                null,
                price == null ? null : new Price(price),
                null,
                new Quantity(quantity),
                clientOrderId
        );
    }

    // ── placeOrder ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test
        @DisplayName("orderId를 UUID 문자열로 반환한다")
        void placeOrder_returnsUuidString() {
            String result = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("PlaceOrder 커맨드를 EngineManager에 제출한다")
        void placeOrder_submitsPlaceOrderCommand() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.PlaceOrder.class);
        }

        @Test
        @DisplayName("커맨드에 담긴 Order의 side/price/quantity가 입력값과 일치한다")
        void placeOrder_commandContainsCorrectOrderFields() {
            sut.placeOrder(limitCommand("BTC", "SELL", 20_000L, 3));

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
            String returnedId = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(returnedId).isEqualTo(cmd.order().getOrderId().toString());
        }

        @Test
        @DisplayName("잘못된 side 값이 전달되면 IllegalArgumentException이 발생한다")
        void placeOrder_invalidSide_throwsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.placeOrder(new PlaceOrderCommand(
                            new Symbol("BTC"),
                            Side.valueOf("INVALID"),
                            OrderType.LIMIT,
                            null,
                            new Price(10_000L),
                            null,
                            new Quantity(5),
                            null
                    )));
        }

        @Test
        @DisplayName("생성된 Order를 ACCEPTED 상태로 orderRepository에 저장한다")
        void placeOrder_savesOrderToRepository() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus().name()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("orderRepository에 저장되는 Order와 EngineManager에 제출되는 Order가 동일 객체다")
        void placeOrder_savedOrderIsSameAsSubmittedOrder() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            ArgumentCaptor<Order> repositoryCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<EngineCommand> engineCaptor = forClass(EngineCommand.class);
            verify(orderRepository).save(repositoryCaptor.capture());
            verify(engineManager).submit(any(Symbol.class), engineCaptor.capture());

            Order savedOrder = repositoryCaptor.getValue();
            Order submittedOrder = ((EngineCommand.PlaceOrder) engineCaptor.getValue()).order();
            assertThat(savedOrder).isSameAs(submittedOrder);
        }

        @Test
        @DisplayName("orderRepository.save는 engineManager.submit 이후에 호출된다")
        void placeOrder_savesOrderAfterSubmittingCommand() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            InOrder inOrder = inOrder(orderRepository, engineManager);
            inOrder.verify(engineManager).submit(any(), any());
            inOrder.verify(orderRepository).save(any());
        }
    }

    // ── TIF 검증 (MVP3-004) ───────────────────────────────────────────────────

    @Nested
    @DisplayName("TIF 검증")
    class TifValidation {

        @Test
        @DisplayName("LIMIT 주문에서 tif=null이면 GTC 기본값으로 생성된다")
        void placeOrder_limitWithNullTif_defaultsToGtc() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            Order order = ((EngineCommand.PlaceOrder) captor.getValue()).order();
            assertThat(order.getTif()).isEqualTo(TimeInForce.GTC);
        }

        @Test
        @DisplayName("LIMIT 주문에서 tif=IOC/FOK/GTC가 허용된다")
        void placeOrder_limitWithExplicitTif_accepted() {
            for (TimeInForce tif : TimeInForce.values()) {
                sut.placeOrder(new PlaceOrderCommand(
                        new Symbol("BTC"),
                        Side.BUY,
                        OrderType.LIMIT,
                        tif,
                        new Price(10_000L),
                        null,
                        new Quantity(5),
                        null
                ));

                ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
                verify(engineManager, atLeastOnce()).submit(any(Symbol.class), captor.capture());
                Order order = ((EngineCommand.PlaceOrder) captor.getValue()).order();
                assertThat(order.getTif()).isEqualTo(tif);
            }
        }

        @Test
        @DisplayName("MARKET 주문에서 tif=null이면 정상 처리된다")
        void placeOrder_marketWithoutTif_accepted() {
            sut.placeOrder(new PlaceOrderCommand(
                    new Symbol("BTC"),
                    Side.BUY,
                    OrderType.MARKET,
                    null,
                    null,
                    new QuoteQty(50_000),
                    null,
                    null
            ));

            verify(engineManager).submit(any(Symbol.class), any(EngineCommand.class));
        }
    }

    // ── clientOrderId 멱등 처리 ───────────────────────────────────────────────

    @Nested
    @DisplayName("clientOrderId 멱등 처리")
    class ClientOrderIdIdempotency {

        @Test
        @DisplayName("clientOrderId가 없으면 동일 파라미터라도 매 호출마다 새 orderId를 반환한다")
        void placeOrder_withoutClientOrderId_returnsNewOrderIdEachCall() {
            String firstId = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, null));
            String secondId = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, null));

            assertThat(firstId).isNotEqualTo(secondId);
        }

        @Test
        @DisplayName("동일 clientOrderId로 재시도하면 기존 orderId를 반환한다")
        void placeOrder_duplicateClientOrderId_returnsSameOrderId() {
            String firstId = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "idempotent-key-001"));
            String secondId = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "idempotent-key-001"));

            assertThat(firstId).isEqualTo(secondId);
            verify(engineManager, times(1)).submit(any(), any());
        }

        @Test
        @DisplayName("동일 clientOrderId로 재시도해도 주문이 중복 생성되지 않는다")
        void placeOrder_duplicateClientOrderId_doesNotCreateDuplicateOrder() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "idempotent-key-002"));

            verify(orderRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("첫 요청이 예외로 실패하면 clientOrderId 맵에서 제거되어 재시도 시 새 주문을 생성한다")
        void placeOrder_firstRequestFails_retryWithSameClientOrderIdCreatesNewOrder() {
            doThrow(new RuntimeException("engine error"))
                    .when(engineManager).submit(any(), any());

            assertThrows(RuntimeException.class,
                    () -> sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "retry-key")));

            doNothing().when(engineManager).submit(any(), any());

            String result = sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, "retry-key"));

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            verify(engineManager, times(2)).submit(any(), any());
        }

        @Test
        @DisplayName("clientOrderId가 빈 문자열이면 멱등성을 적용하지 않는다")
        void placeOrder_blankClientOrderId_treatedAsNoClientOrderId() {
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, ""));
            sut.placeOrder(limitCommand("BTC", "BUY", 10_000L, 5, ""));

            verify(engineManager, times(2)).submit(any(), any());
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
        @DisplayName("orderRepository에 주문이 없으면 OrderNotFoundException이 발생한다")
        void cancelOrder_orderNotFound_throwsOrderNotFoundException() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.cancelOrder(orderId));
        }

        @Test
        @DisplayName("cancelOrder는 orderRepository.save()를 호출하지 않는다 (취소 저장은 EngineHandler 담당)")
        void cancelOrder_doesNotSaveToRepository() {
            String orderId = UUID.randomUUID().toString();
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(orderId);

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("MARKET 주문 취소 시 OrderNotCancellableException이 발생한다")
        void cancelMarketOrder_throwsOrderNotCancellableException() {
            String orderId = UUID.randomUUID().toString();
            Order marketOrder = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, new Symbol("BTC"), new QuoteQty(50_000));
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(marketOrder));

            assertThrows(OrderNotCancellableException.class, () -> sut.cancelOrder(orderId));
            verify(engineManager, never()).submit(any(), any());
        }

        @Test
        @DisplayName("이미 CANCELLED된 주문 취소 시 OrderAlreadyFinalizedException이 발생한다")
        void cancelAlreadyFinalized_throwsOrderAlreadyFinalizedException() {
            String orderId = UUID.randomUUID().toString();
            Order order = OrderFixture.createLimit(Side.BUY, new Symbol("BTC"), TimeInForce.GTC, new Price(10_000), new Quantity(5));
            order.activate();
            order.cancel(); // → CANCELLED 상태

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderAlreadyFinalizedException.class, () -> sut.cancelOrder(orderId));
            verify(engineManager, never()).submit(any(), any());
        }
    }
}
