package dev.junyoung.trading.order.application.service;

import org.springframework.stereotype.Service;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.exception.order.OrderAlreadyFinalizedException;
import dev.junyoung.trading.order.application.exception.order.OrderNotCancellableException;
import dev.junyoung.trading.order.application.exception.order.OrderNotFoundException;
import dev.junyoung.trading.order.application.port.in.CancelOrderUseCase;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements PlaceOrderUseCase, CancelOrderUseCase {

    private final ConcurrentHashMap<String, CompletableFuture<OrderId>> clientOrderMap = new ConcurrentHashMap<>();

    private final EngineManager engineManager;
    private final OrderRepository orderRepository;

    @Override
    public String placeOrder(PlaceOrderCommand command) {
        String clientOrderId = command.clientOrderId();
        boolean hasClientOrderId = clientOrderId != null && !clientOrderId.isBlank();

        CompletableFuture<OrderId> future = null;
        if (hasClientOrderId) {
            future = new CompletableFuture<>();
            CompletableFuture<OrderId> existing = clientOrderMap.putIfAbsent(clientOrderId, future);
            if (existing != null) {
                try {
                    return existing.join().toString();
                } catch (CompletionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    throw e;
                }
            }
        }

        try {
            Order order = Order.create(command.symbol(),
                command.side(),
                command.orderType(),
                command.tif(),
                command.price(),
                command.quoteQty(),
                command.quantity()
            );

            engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));
            orderRepository.save(order);  // ACCEPTED 상태로 최초 저장 (참조 공유로 이후 상태 변경 자동 반영)

            OrderId orderId = order.getOrderId();
            if (hasClientOrderId) future.complete(orderId);
            return orderId.toString(); // Phase 3 알려진 제약: 성공 항목을 맵에서 제거하지 않아 재시작 전까지 누적됨
        } catch (Exception e) {
            if (hasClientOrderId) {
                future.completeExceptionally(e);
                clientOrderMap.remove(clientOrderId, future);
            }
            throw e;
        }
    }

    @Override
    public void cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.isMarket())
            throw new OrderNotCancellableException(orderId);

        if (order.getStatus().isFinal())
            throw new OrderAlreadyFinalizedException(orderId);

        engineManager.submit(order.getSymbol(), new EngineCommand.CancelOrder(OrderId.from(orderId)));
    }
}
