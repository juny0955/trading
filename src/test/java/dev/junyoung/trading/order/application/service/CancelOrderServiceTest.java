package dev.junyoung.trading.order.application.service;

import java.util.Optional;
import java.util.UUID;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.exception.order.OrderAlreadyFinalizedException;
import dev.junyoung.trading.order.application.exception.order.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderService")
class CancelOrderServiceTest {

    private static final AccountId ACCOUNT_ID =
            new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final String ACCOUNT_ID_RAW = ACCOUNT_ID.value().toString();
    private static final String OTHER_ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";

    @Mock
    private EngineManager engineManager;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CancelOrderService sut;

    private Order buyOrder(String symbol) {
        return OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, new Symbol(symbol), TimeInForce.GTC, new Price(10_000), new Quantity(5));
    }

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("동일 account 소유 주문이면 CancelOrder 커맨드를 엔진에 제출한다")
        void cancelOrder_submitsCancelOrderCommand() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString());

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.CancelOrder.class);
        }

        @Test
        @DisplayName("취소 커맨드의 orderId는 입력값과 일치한다")
        void cancelOrder_commandContainsCorrectOrderId() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString());

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.CancelOrder cmd = (EngineCommand.CancelOrder) captor.getValue();
            assertThat(cmd.orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("주문이 없으면 OrderNotFoundException이 발생한다")
        void cancelOrder_orderNotFound_throwsOrderNotFoundException() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString()));
        }

        @Test
        @DisplayName("다른 account의 주문이면 OrderNotFoundException이 발생한다")
        void cancelOrder_otherAccountOrder_throwsOrderNotFoundException() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            assertThrows(OrderNotFoundException.class, () -> sut.cancelOrder(OTHER_ACCOUNT_ID, orderId.toString()));
            verify(engineManager, never()).submit(any(), any());
        }

        @Test
        @DisplayName("취소 요청 자체는 저장소에 다시 저장하지 않는다")
        void cancelOrder_doesNotSaveToRepository() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(buyOrder("BTC")));

            sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString());

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("MARKET 주문은 취소할 수 없다")
        void cancelMarketOrder_throwsOrderNotCancellableException() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            Order marketOrder = OrderFixture.createMarketBuyWithQuoteQty(ACCOUNT_ID, Side.BUY, new Symbol("BTC"), new QuoteQty(50_000));
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(marketOrder));

            assertThrows(OrderNotCancellableException.class, () -> sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString()));
            verify(engineManager, never()).submit(any(), any());
        }

        @Test
        @DisplayName("이미 종료된 주문은 취소할 수 없다")
        void cancelAlreadyFinalized_throwsOrderAlreadyFinalizedException() {
            OrderId orderId = new OrderId(UUID.randomUUID());
            Order order = OrderFixture.createLimit(ACCOUNT_ID, Side.BUY, new Symbol("BTC"), TimeInForce.GTC, new Price(10_000), new Quantity(5))
                    .activate().cancel();

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderAlreadyFinalizedException.class, () -> sut.cancelOrder(ACCOUNT_ID_RAW, orderId.toString()));
            verify(engineManager, never()).submit(any(), any());
        }
    }
}
