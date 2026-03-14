package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderService")
class PlaceOrderServiceTest {

    private static final AccountId ACCOUNT_ID =
            new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final AccountId OTHER_ACCOUNT_ID =
            new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Mock
    private EngineManager engineManager;

    @Mock
    private AcceptedSeqGenerator acceptedSeqGenerator;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PlaceOrderService sut;

    private PlaceOrderCommand limitCommand(AccountId accountId, String symbol, String side, Long price, int quantity, String clientOrderId) {
        return new PlaceOrderCommand(
                accountId,
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

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test
        @DisplayName("orderId를 UUID 문자열로 반환한다")
        void placeOrder_returnsUuidString() {
            String result = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-001"));

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("PlaceOrder 커맨드를 EngineManager에 제출한다")
        void placeOrder_submitsPlaceOrderCommand() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-002"));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.PlaceOrder.class);
        }

        @Test
        @DisplayName("생성된 주문은 accountId와 입력 필드를 유지한다")
        void placeOrder_commandContainsCorrectOrderFields() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "SELL", 20_000L, 3, "client-003"));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(cmd.order().getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(cmd.order().getSide().name()).isEqualTo("SELL");
            assertThat(cmd.order().getLimitPriceOrThrow().value()).isEqualTo(20_000L);
            assertThat(cmd.order().getQuantity().value()).isEqualTo(3L);
        }

        @Test
        @DisplayName("반환된 orderId는 생성된 Order의 orderId와 같다")
        void placeOrder_returnedOrderIdMatchesCommandOrderId() {
            String returnedId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-004"));

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(returnedId).isEqualTo(cmd.order().getOrderId().toString());
        }

        @Test
        @DisplayName("생성된 Order는 ACCEPTED 상태로 저장된다")
        void placeOrder_savesOrderToRepository() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-005"));

            ArgumentCaptor<Order> captor = forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus().name()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("저장한 Order와 엔진에 제출한 Order는 동일 객체다")
        void placeOrder_savedOrderIsSameAsSubmittedOrder() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-006"));

            ArgumentCaptor<Order> repositoryCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<EngineCommand> engineCaptor = forClass(EngineCommand.class);
            verify(orderRepository).save(repositoryCaptor.capture());
            verify(engineManager).submit(any(Symbol.class), engineCaptor.capture());

            Order savedOrder = repositoryCaptor.getValue();
            Order submittedOrder = ((EngineCommand.PlaceOrder) engineCaptor.getValue()).order();
            assertThat(savedOrder).isSameAs(submittedOrder);
        }

        @Test
        @DisplayName("order를 저장한 이후에 engine에 submit한다")
        void placeOrder_savesOrderBeforeSubmittingCommand() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-007"));

            InOrder inOrder = inOrder(orderRepository, engineManager);
            inOrder.verify(orderRepository).save(any());
            inOrder.verify(engineManager).submit(any(), any());
        }
    }

    @Nested
    @DisplayName("TIF validation")
    class TifValidation {

        @Test
        @DisplayName("LIMIT 주문에서 tif=null이면 GTC가 기본값이다")
        void placeOrder_limitWithNullTif_defaultsToGtc() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-008"));

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
                        ACCOUNT_ID,
                        new Symbol("BTC"),
                        Side.BUY,
                        OrderType.LIMIT,
                        tif,
                        new Price(10_000L),
                        null,
                        new Quantity(5),
                        "client-tif-" + tif.name()
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
                    ACCOUNT_ID,
                    new Symbol("BTC"),
                    Side.BUY,
                    OrderType.MARKET,
                    null,
                    null,
                    new QuoteQty(50_000),
                    null,
                    "client-market-001"
            ));

            verify(engineManager).submit(any(Symbol.class), any(EngineCommand.class));
        }
    }

    @Nested
    @DisplayName("idempotency")
    class ClientOrderIdIdempotency {

        @Test
        @DisplayName("동일 accountId와 clientOrderId 요청은 같은 orderId를 반환한다")
        void placeOrder_duplicateClientOrderId_returnsSameOrderId() {
            String firstId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-001"));
            String secondId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-001"));

            assertThat(firstId).isEqualTo(secondId);
            verify(engineManager, times(1)).submit(any(), any());
        }

        @Test
        @DisplayName("동일 accountId와 clientOrderId 재시도는 주문을 중복 생성하지 않는다")
        void placeOrder_duplicateClientOrderId_doesNotCreateDuplicateOrder() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));

            verify(orderRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("서로 다른 account는 같은 clientOrderId를 사용해도 충돌하지 않는다")
        void placeOrder_sameClientOrderIdDifferentAccounts_createsDifferentOrders() {
            String firstId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "shared-key"));
            String secondId = sut.placeOrder(limitCommand(OTHER_ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "shared-key"));

            assertThat(firstId).isNotEqualTo(secondId);
            verify(engineManager, times(2)).submit(any(), any());
            verify(orderRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("첫 요청이 실패하면 같은 키로 재시도할 수 있다")
        void placeOrder_firstRequestFails_retryWithSameClientOrderIdCreatesNewOrder() {
            doThrow(new RuntimeException("engine error"))
                    .doNothing()
                    .when(engineManager).submit(any(), any());

            assertThrows(RuntimeException.class,
                    () -> sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "retry-key")));

            String result = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "retry-key"));

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            verify(engineManager, times(2)).submit(any(), any());
        }
    }
}
