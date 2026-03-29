package dev.junyoung.trading.order.application.service;

import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.service.BalanceHoldPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCompensationService {

    private final OrderRepository orderRepository;
    private final HoldReservationPort holdReservationPort;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(Order order) {
        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);

        orderRepository.deleteById(order.getOrderId());
        holdReservationPort.release(order.getAccountId(), holdSpec.asset(), holdSpec.amount());
        idempotencyKeyRepository.delete(order.getAccountId(), order.getClientOrderId());
    }
}
