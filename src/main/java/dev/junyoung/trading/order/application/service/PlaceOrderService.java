package dev.junyoung.trading.order.application.service;

import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.junyoung.trading.account.application.exception.account.AccountNotFoundException;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.metrics.OrderMetrics;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.AccountQueryPort;
import dev.junyoung.trading.order.application.port.out.EngineCommandPort;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.service.BalanceHoldPolicy;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlaceOrderService implements PlaceOrderUseCase {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AcceptedSeqGenerator acceptedSeqGenerator;
    private final OrderRepository orderRepository;

    private final OrderCompensationService orderCompensationService;

    private final AccountQueryPort accountQueryPort;
    private final HoldReservationPort holdReservationPort;
    private final EngineCommandPort engineCommandPort;

    private final OrderMetrics orderMetrics;

    @Override
    public OrderId placeOrder(PlaceOrderCommand command) {
        Instant serviceEnteredAt = Instant.now();

        OrderId orderId = OrderId.newId();
        try {
            idempotencyKeyRepository.save(command.accountId(), orderId, command.clientOrderId());
        } catch (DuplicateKeyException e) {
            orderMetrics.incrementIdempotencyConflict();
            return idempotencyKeyRepository.findOrderId(command.accountId(), command.clientOrderId());
        }

        orderMetrics.incrementPlaceOrderTps();

        validateAccount(command.accountId());

        long acceptedSeq = acceptedSeqGenerator.next();
        Order order = createOrder(orderId, acceptedSeq, command);

        Timer.Sample acceptTxSample = Timer.start();
        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);
        holdReservationPort.reserve(order.getAccountId(), holdSpec.asset(), holdSpec.amount());
        orderRepository.save(order);
        acceptTxSample.stop(orderMetrics.orderAcceptTxTimer());

        submitEngine(order, serviceEnteredAt);

        return order.getOrderId();
    }

    private void validateAccount(AccountId accountId) {
        if (!accountQueryPort.existsById(accountId))
            throw new AccountNotFoundException(accountId.toString());
    }

    private void submitEngine(Order order, Instant serviceEnteredAt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    engineCommandPort.submitPlace(order.getSymbol(), order, serviceEnteredAt);
                    orderMetrics.incrementAcceptedOrderTps();
                } catch (Exception e) {
                    try {
                        orderCompensationService.compensate(order);
                        orderMetrics.incrementQueueFullRollback();
                    } catch (Exception ex) {
                        log.error("Order Compensation Error orderId={}, accountId={}, clientOrderId={}",
                            order.getOrderId(),
                            order.getAccountId(),
                            order.getClientOrderId(),
                            ex
                        );
                        e.addSuppressed(ex);
                    }
                    throw e;
                }
            }
        });
    }

    private Order createOrder(OrderId orderId, long acceptedSeq, PlaceOrderCommand command) {
        return Order.create(
            orderId,
            command.accountId(),
            command.clientOrderId(),
            acceptedSeq,
            command.symbol(),
            command.side(),
            command.orderType(),
            command.tif(),
            command.price(),
            command.quoteQty(),
            command.quantity()
        );
    }

}
