package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import org.springframework.stereotype.Service;

import dev.junyoung.trading.order.application.engine.loop.EngineCommand;
import dev.junyoung.trading.order.application.port.out.AcceptedSeqGenerator;
import dev.junyoung.trading.order.application.port.out.OrderCommandGateway;
import dev.junyoung.trading.order.application.exception.order.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOrderService implements CancelOrderUseCase {

    private final AcceptedSeqGenerator acceptedSeqGenerator;
    private final OrderCommandGateway engineCommandGateway;
    private final OrderRepository orderRepository;

    @Override
    public void cancelOrder(String accountId, String orderId) {
        Order order = orderRepository.findById(OrderId.from(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getAccountId().equals(AccountId.from(accountId)))
            throw new OrderNotFoundException(orderId);

        if (order.isMarket())
            throw new OrderNotCancellableException(orderId);

        if (order.isFinal()) {
            log.info("Cancel request ignored — order already final (idempotent): orderId={}, status={}", orderId, order.getStatus());
            return;
        }

        long acceptedSeq = acceptedSeqGenerator.next();
        engineCommandGateway.submit(order.getSymbol(), new EngineCommand.CancelOrder(acceptedSeq, order.getOrderId(), order.getAccountId(), null, null));
    }
}
