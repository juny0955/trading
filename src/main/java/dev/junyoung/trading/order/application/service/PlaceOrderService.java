package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.account.application.exception.account.AccountNotFoundException;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.*;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.service.BalanceHoldPolicy;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlaceOrderService implements PlaceOrderUseCase {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AcceptedSeqGenerator acceptedSeqGenerator;
    private final AccountQueryPort accountQueryPort;
    private final HoldReservationPort holdReservationPort;
    private final OrderRepository orderRepository;
    private final EngineManager engineManager;
    private final OrderCompensationService orderCompensationService;

    private final Counter queueFullRollbackCount;

    @Override
    public OrderId placeOrder(PlaceOrderCommand command) {
        OrderId orderId = OrderId.newId();
        try {
            idempotencyKeyRepository.save(command.accountId(), orderId, command.clientOrderId());
        } catch (DuplicateKeyException e) {
            return idempotencyKeyRepository.findOrderId(command.accountId(), command.clientOrderId());
        }

        validateAccount(command.accountId());

        long acceptedSeq = acceptedSeqGenerator.next();
        Order order = createOrder(orderId, acceptedSeq, command);

        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);
        holdReservationPort.reserve(order.getAccountId(), holdSpec.asset(), holdSpec.amount());
        orderRepository.save(order);

        submitEngine(order);

        return order.getOrderId();
    }

    private void validateAccount(AccountId accountId) {
        if (!accountQueryPort.existsById(accountId))
            throw new AccountNotFoundException(accountId.toString());
    }

    private void submitEngine(Order order) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));
                } catch (Exception e) {
                    try {
                        orderCompensationService.compensate(order);
                        queueFullRollbackCount.increment();
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
