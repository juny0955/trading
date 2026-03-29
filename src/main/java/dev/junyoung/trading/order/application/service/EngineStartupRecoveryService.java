package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.engine.application.metrics.ReplayMetrics;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.BalanceHoldPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EngineStartupRecoveryService {

    private final OrderRepository orderRepository;
    private final HoldReservationPort holdReservationPort;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ReplayMetrics replayMetrics;

    @Transactional
    public void cleanupOrphanAccepted(Symbol symbol) {
        List<Order> orphans = orderRepository.findAcceptedOrdersBySymbol(symbol);
        replayMetrics.incrementOrphanCancelledCount(orphans.size());

        for (Order orphan : orphans) {
            Order cancelled = orphan.cancelOrphan();
            BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(orphan);

            orderRepository.save(cancelled);
            holdReservationPort.release(orphan.getAccountId(), holdSpec.asset(), holdSpec.amount());
            idempotencyKeyRepository.delete(orphan.getAccountId(), orphan.getClientOrderId());

            log.warn("[StartupRecovery] Orphan ACCEPTED order cancelled: orderId={}, accountId={}, symbol={}, clientOrderId={}",
                orphan.getOrderId(), orphan.getAccountId(), symbol.value(), orphan.getClientOrderId());
        }

        replayMetrics.updateConsistencyCheck(symbol.value(), 1);
    }
}
