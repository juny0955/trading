package dev.junyoung.trading.order.application.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.account.application.exception.AccountNotFoundException;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.AccountQueryPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PlaceOrderService implements PlaceOrderUseCase {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AcceptedSeqGenerator acceptedSeqGenerator;
    private final AccountQueryPort accountQueryPort;
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

        if (!accountQueryPort.existsById(command.accountId()))
            throw new AccountNotFoundException(command.accountId().toString());

        long acceptedSeq = acceptedSeqGenerator.next();
        Order order = createOrder(orderId, acceptedSeq, command);

        orderRepository.save(order);
        engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));

        return order.getOrderId();
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
