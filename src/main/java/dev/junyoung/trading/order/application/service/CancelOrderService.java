package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import org.springframework.stereotype.Service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.exception.order.OrderAlreadyFinalizedException;
import dev.junyoung.trading.order.application.exception.order.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CancelOrderService implements CancelOrderUseCase {

    private final EngineManager engineManager;
    private final OrderRepository orderRepository;

    @Override
    public void cancelOrder(String accountId, String orderId) {
        Order order = orderRepository.findById(OrderId.from(orderId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getAccountId().equals(AccountId.from(accountId)))
            throw new OrderNotFoundException(orderId);

        if (order.isMarket())
            throw new OrderNotCancellableException(orderId);

        if (order.isFinal())
            throw new OrderAlreadyFinalizedException(orderId);

        engineManager.submit(order.getSymbol(), new EngineCommand.CancelOrder(OrderId.from(orderId), AccountId.from(accountId)));
    }
}
