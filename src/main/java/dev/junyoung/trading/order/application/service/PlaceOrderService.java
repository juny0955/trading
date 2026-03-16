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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional
public class PlaceOrderService implements PlaceOrderUseCase {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AcceptedSeqGenerator acceptedSeqGenerator;
    private final AccountQueryPort accountQueryPort;
    private final HoldReservationPort holdReservationPort;
    private final OrderRepository orderRepository;
    private final EngineManager engineManager;

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
                engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));
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
