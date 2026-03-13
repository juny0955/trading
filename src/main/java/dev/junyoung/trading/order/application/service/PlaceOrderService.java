package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.application.engine.EngineManager;
import dev.junyoung.trading.order.application.port.in.PlaceOrderUseCase;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PlaceOrderService implements PlaceOrderUseCase {

    private record PlaceOrderIdempotencyKey(AccountId accountId, String clientOrderId) {}
    private final ConcurrentHashMap<PlaceOrderIdempotencyKey, CompletableFuture<OrderId>> clientOrderMap = new ConcurrentHashMap<>();

    private final EngineManager engineManager;
    private final OrderRepository orderRepository;

    @Override
    // 알려진 제약: 성공 항목을 맵에서 제거하지 않아 재시작 전까지 누적됨
    public String placeOrder(PlaceOrderCommand command) {
        CompletableFuture<OrderId> future = new CompletableFuture<>();
        PlaceOrderIdempotencyKey key = new PlaceOrderIdempotencyKey(command.accountId(), command.clientOrderId());
        CompletableFuture<OrderId> existing = clientOrderMap.putIfAbsent(key, future);

        if (existing != null) {
            // 동일 멱등 키의 선행 요청이 있으면 그 결과를 그대로 재사용한다.
            try {
                return existing.join().toString();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw e;
            }
        }

        try {
            Order order = createOrder(command);

            engineManager.submit(order.getSymbol(), new EngineCommand.PlaceOrder(order));
            orderRepository.save(order);  // ACCEPTED 상태로 최초 저장 (참조 공유로 이후 상태 변경 자동 반영)

            OrderId orderId = order.getOrderId();
            // 후행 중복 요청이 동일 orderId를 받을 수 있도록 완료 결과를 남긴다.
            future.complete(orderId);
            return orderId.toString();
        } catch (Exception e) {
            // 실패한 시도는 키를 제거해 같은 요청이 다시 진입할 수 있게 한다.
            future.completeExceptionally(e);
            clientOrderMap.remove(key, future);
            throw e;
        }
    }

    private Order createOrder(PlaceOrderCommand command) {
        return Order.create(
            command.accountId(),
            command.clientOrderId(),
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
