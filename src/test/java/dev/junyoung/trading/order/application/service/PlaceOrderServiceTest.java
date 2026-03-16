package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;

import dev.junyoung.trading.account.application.exception.account.AccountNotFoundException;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.AccountQueryPort;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
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

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private AccountQueryPort accountQueryPort;

    @Mock
    private HoldReservationPort holdReservationPort;

    @Mock
    private OrderCompensationService orderCompensationService;

    @Mock
    private Counter queueFullRollbackCount;

    @InjectMocks
    private PlaceOrderService sut;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        lenient().when(accountQueryPort.existsById(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 등록된 afterCommit 콜백을 모두 실행한다.
     * clear → re-init 으로 이후 추가 호출에서도 등록이 가능하다.
     */
    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.initSynchronization();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }

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
            OrderId result = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-001"));

            assertThat(result.toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("PlaceOrder 커맨드를 EngineManager에 제출한다")
        void placeOrder_submitsPlaceOrderCommand() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-002"));
            triggerAfterCommit();

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());
            assertThat(captor.getValue()).isInstanceOf(EngineCommand.PlaceOrder.class);
        }

        @Test
        @DisplayName("생성된 주문은 accountId와 입력 필드를 유지한다")
        void placeOrder_commandContainsCorrectOrderFields() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "SELL", 20_000L, 3, "client-003"));
            triggerAfterCommit();

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
            OrderId returnedId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-004"));
            triggerAfterCommit();

            ArgumentCaptor<EngineCommand> captor = forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), captor.capture());

            EngineCommand.PlaceOrder cmd = (EngineCommand.PlaceOrder) captor.getValue();
            assertThat(returnedId).isEqualTo(cmd.order().getOrderId());
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
            triggerAfterCommit();

            ArgumentCaptor<Order> repositoryCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<EngineCommand> engineCaptor = forClass(EngineCommand.class);
            verify(orderRepository).save(repositoryCaptor.capture());
            verify(engineManager).submit(any(Symbol.class), engineCaptor.capture());

            Order savedOrder = repositoryCaptor.getValue();
            Order submittedOrder = ((EngineCommand.PlaceOrder) engineCaptor.getValue()).order();
            assertThat(savedOrder).isSameAs(submittedOrder);
        }

        @Test
        @DisplayName("멱등성 검사 → 계좌 검증 → hold 예약 → 주문 저장 → 엔진 제출 순서로 실행한다")
        void placeOrder_savesOrderBeforeSubmittingCommand() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "client-007"));
            triggerAfterCommit();

            InOrder inOrder = inOrder(idempotencyKeyRepository, accountQueryPort, holdReservationPort, orderRepository, engineManager);
            inOrder.verify(idempotencyKeyRepository).save(any(), any(), any());
            inOrder.verify(accountQueryPort).existsById(any());
            inOrder.verify(holdReservationPort).reserve(any(), any(), anyLong());
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
            triggerAfterCommit();

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
                triggerAfterCommit();

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
            triggerAfterCommit();

            verify(engineManager).submit(any(Symbol.class), any(EngineCommand.class));
        }
    }

    @Nested
    @DisplayName("idempotency")
    class ClientOrderIdIdempotency {

        @Test
        @DisplayName("동일 accountId와 clientOrderId 요청은 같은 orderId를 반환한다")
        void placeOrder_duplicateClientOrderId_returnsSameOrderId() {
            OrderId firstId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-001"));

            doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(ACCOUNT_ID, "idempotent-key-001"))
                    .thenReturn(firstId);

            OrderId secondId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-001"));
            triggerAfterCommit();

            assertThat(firstId).isEqualTo(secondId);
            verify(engineManager, times(1)).submit(any(), any());
        }

        @Test
        @DisplayName("동일 accountId와 clientOrderId 재시도는 주문을 중복 생성하지 않는다")
        void placeOrder_duplicateClientOrderId_doesNotCreateDuplicateOrder() {
            doNothing()
                    .doThrow(new DuplicateKeyException("duplicate"))
                    .doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(any(), any())).thenReturn(OrderId.newId());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "idempotent-key-002"));

            verify(orderRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("서로 다른 account는 같은 clientOrderId를 사용해도 충돌하지 않는다")
        void placeOrder_sameClientOrderIdDifferentAccounts_createsDifferentOrders() {
            OrderId firstId = sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "shared-key"));
            OrderId secondId = sut.placeOrder(limitCommand(OTHER_ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "shared-key"));
            triggerAfterCommit();

            assertThat(firstId).isNotEqualTo(secondId);
            verify(engineManager, times(2)).submit(any(), any());
            verify(orderRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("DuplicateKeyException 발생 시 orderRepository와 engineManager는 호출되지 않는다")
        void placeOrder_duplicateKey_doesNotCallOrderRepositoryOrEngine() {
            doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(any(), any())).thenReturn(OrderId.newId());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "dup-key"));

            verify(orderRepository, never()).save(any());
            verify(engineManager, never()).submit(any(), any());
        }

        @Test
        @DisplayName("idempotencyKey는 올바른 accountId와 clientOrderId로 저장된다")
        void placeOrder_idempotencyKeySavedWithCorrectFields() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "check-key"));

            ArgumentCaptor<OrderId> orderIdCaptor = ArgumentCaptor.forClass(OrderId.class);
            verify(idempotencyKeyRepository).save(eq(ACCOUNT_ID), orderIdCaptor.capture(), eq("check-key"));
            assertThat(orderIdCaptor.getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("accepted seq")
    class AcceptedSeqValidation {

        @Test
        @DisplayName("주문 생성 시 acceptedSeqGenerator.next()를 호출한다")
        void placeOrder_callsAcceptedSeqGenerator() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "seq-001"));

            verify(acceptedSeqGenerator).next();
        }

        @Test
        @DisplayName("acceptedSeqGenerator가 반환한 값이 Order에 설정된다")
        void placeOrder_acceptedSeqIsSetOnOrder() {
            when(acceptedSeqGenerator.next()).thenReturn(42L);

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "seq-002"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getAcceptedSeq()).isEqualTo(42L);
        }

        @Test
        @DisplayName("중복 키 early-return 시 acceptedSeqGenerator를 호출하지 않는다")
        void placeOrder_duplicateKey_skipsAcceptedSeqGenerator() {
            doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(any(), any())).thenReturn(OrderId.newId());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "seq-003"));

            verify(acceptedSeqGenerator, never()).next();
        }
    }

    @Nested
    @DisplayName("account validation")
    class AccountValidation {

        @Test
        @DisplayName("존재하지 않는 accountId로 주문 시 AccountNotFoundException을 던진다")
        void placeOrder_accountNotFound_throwsException() {
            when(accountQueryPort.existsById(ACCOUNT_ID)).thenReturn(false);

            assertThatThrownBy(() -> sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "acct-001")))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("계좌가 없으면 orderRepository와 engineManager를 호출하지 않는다")
        void placeOrder_accountNotFound_doesNotSaveOrSubmit() {
            when(accountQueryPort.existsById(ACCOUNT_ID)).thenReturn(false);

            assertThatThrownBy(() -> sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "acct-002")))
                    .isInstanceOf(AccountNotFoundException.class);

            verify(orderRepository, never()).save(any());
            verify(engineManager, never()).submit(any(), any());
        }

        @Test
        @DisplayName("existsById는 command의 accountId로 호출된다")
        void placeOrder_existsById_calledWithCorrectAccountId() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "acct-003"));

            verify(accountQueryPort).existsById(ACCOUNT_ID);
        }

        @Test
        @DisplayName("중복 키 early-return 시 accountQueryPort를 호출하지 않는다")
        void placeOrder_duplicateKey_skipsAccountValidation() {
            doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(any(), any())).thenReturn(OrderId.newId());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "acct-004"));

            verify(accountQueryPort, never()).existsById(any());
        }
    }

    @Nested
    @DisplayName("balance hold")
    class BalanceHold {

        @Test
        @DisplayName("LIMIT BUY: holdReservationPort.reserve()가 KRW와 price*quantity로 호출된다")
        void placeOrder_limitBuy_reservesKrwWithPriceTimesQuantity() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 3, "hold-001"));

            verify(holdReservationPort).reserve(eq(ACCOUNT_ID), eq(new Asset("KRW")), eq(30_000L));
        }

        @Test
        @DisplayName("LIMIT SELL: holdReservationPort.reserve()가 심볼 자산과 quantity로 호출된다")
        void placeOrder_limitSell_reservesSymbolAssetWithQuantity() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "SELL", 10_000L, 3, "hold-002"));

            verify(holdReservationPort).reserve(eq(ACCOUNT_ID), eq(new Asset("BTC")), eq(3L));
        }

        @Test
        @DisplayName("잔고 홀드는 orderRepository.save() 전에 호출된다")
        void placeOrder_holdReservedBeforeOrderSave() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "hold-003"));

            InOrder inOrder = inOrder(holdReservationPort, orderRepository);
            inOrder.verify(holdReservationPort).reserve(any(), any(), anyLong());
            inOrder.verify(orderRepository).save(any());
        }

        @Test
        @DisplayName("계좌가 없으면 holdReservationPort를 호출하지 않는다")
        void placeOrder_accountNotFound_doesNotCallHoldReservation() {
            when(accountQueryPort.existsById(ACCOUNT_ID)).thenReturn(false);

            assertThatThrownBy(() -> sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "hold-004")))
                    .isInstanceOf(AccountNotFoundException.class);

            verify(holdReservationPort, never()).reserve(any(), any(), anyLong());
        }

        @Test
        @DisplayName("중복 키 early-return 시 holdReservationPort를 호출하지 않는다")
        void placeOrder_duplicateKey_doesNotCallHoldReservation() {
            doThrow(new DuplicateKeyException("duplicate"))
                    .when(idempotencyKeyRepository).save(any(), any(), any());
            when(idempotencyKeyRepository.findOrderId(any(), any())).thenReturn(OrderId.newId());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "hold-005"));

            verify(holdReservationPort, never()).reserve(any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("engine submit failure")
    class EngineSubmitFailure {

        @Test
        @DisplayName("engineManager.submit() 예외 시 orderCompensationService.compensate(order)가 호출된다")
        void engineSubmit_failure_callsCompensate() {
            doThrow(new RuntimeException("engine error"))
                    .when(engineManager).submit(any(), any());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "comp-001"));
            assertThatThrownBy(() -> triggerAfterCommit())
                    .isInstanceOf(RuntimeException.class);

            verify(orderCompensationService).compensate(any(Order.class));
        }

        @Test
        @DisplayName("engineManager.submit() 성공 시 orderCompensationService.compensate()를 호출하지 않는다")
        void engineSubmit_success_doesNotCallCompensate() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "comp-002"));
            triggerAfterCommit();

            verify(orderCompensationService, never()).compensate(any());
        }

        @Test
        @DisplayName("compensate()에 전달된 Order는 submit 대상 Order와 동일하다")
        void engineSubmit_failure_compensateReceivesSameOrder() {
            doThrow(new RuntimeException("engine error"))
                    .when(engineManager).submit(any(), any());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "comp-003"));
            assertThatThrownBy(() -> triggerAfterCommit())
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<EngineCommand> engineCaptor = ArgumentCaptor.forClass(EngineCommand.class);
            verify(engineManager).submit(any(Symbol.class), engineCaptor.capture());

            ArgumentCaptor<Order> compensateCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderCompensationService).compensate(compensateCaptor.capture());

            Order submittedOrder = ((EngineCommand.PlaceOrder) engineCaptor.getValue()).order();
            assertThat(compensateCaptor.getValue()).isSameAs(submittedOrder);
        }

        @Test
        @DisplayName("engineManager.submit() 실패 후 compensation 성공 시 queueFullRollbackCount가 증가한다")
        void engineSubmit_failure_compensateSucceeds_incrementsCounter() {
            doThrow(new RuntimeException("engine error"))
                    .when(engineManager).submit(any(), any());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "counter-001"));
            assertThatThrownBy(() -> triggerAfterCommit())
                    .isInstanceOf(RuntimeException.class);

            verify(queueFullRollbackCount, times(1)).increment();
        }

        @Test
        @DisplayName("compensation도 실패하면 queueFullRollbackCount를 증가시키지 않는다")
        void engineSubmit_failure_compensateFails_doesNotIncrementCounter() {
            doThrow(new RuntimeException("engine error"))
                    .when(engineManager).submit(any(), any());
            doThrow(new RuntimeException("compensation error"))
                    .when(orderCompensationService).compensate(any());

            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "counter-002"));
            assertThatThrownBy(() -> triggerAfterCommit())
                    .isInstanceOf(RuntimeException.class);

            verify(queueFullRollbackCount, never()).increment();
        }

        @Test
        @DisplayName("engineManager.submit() 성공 시 queueFullRollbackCount를 증가시키지 않는다")
        void engineSubmit_success_doesNotIncrementCounter() {
            sut.placeOrder(limitCommand(ACCOUNT_ID, "BTC", "BUY", 10_000L, 5, "counter-003"));
            triggerAfterCommit();

            verify(queueFullRollbackCount, never()).increment();
        }
    }
}