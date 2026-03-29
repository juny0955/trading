package dev.junyoung.trading.order.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 주문 애플리케이션 계층의 계측 포인트를 관리한다.
 *
 * <pre>
 * placeOrder duplicate -> idempotency conflict count 증가
 * queue submit 실패 -> compensation 성공 -> queue rollback count 증가
 * </pre>
 *
 * 외부 진입점은 {@link #incrementIdempotencyConflict()}와
 * {@link #incrementQueueFullRollback()}이다.
 */
@Component
public class OrderMetrics {

    private final Counter idempotencyConflictCounter;
    private final Counter queueFullRollbackCounter;
    private final Counter placeOrderTpsCounter;
    private final Counter acceptedOrderTpsCounter;
    private final Counter cancelOrderTpsCounter;
    private final Timer orderAcceptTxLatencyTimer;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.idempotencyConflictCounter = Counter.builder("idempotency_conflict_count")
            .description("Number of idempotency conflicts during place order")
            .register(meterRegistry);
        this.queueFullRollbackCounter = Counter.builder("queue_full_rollback_count")
            .description("Number of successful compensating rollbacks after engine queue submit failure")
            .register(meterRegistry);
        this.placeOrderTpsCounter = Counter.builder("place_order_tps")
            .description("Number of place order requests received")
            .register(meterRegistry);
        this.acceptedOrderTpsCounter = Counter.builder("accepted_order_tps")
            .description("Number of orders accepted and queued to engine")
            .register(meterRegistry);
        this.cancelOrderTpsCounter = Counter.builder("cancel_order_tps")
            .description("Number of cancel order requests received")
            .register(meterRegistry);
        this.orderAcceptTxLatencyTimer = Timer.builder("order_accept_tx_latency")
            .description("Duration of order acceptance transaction (hold reserve + order save)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // 계측
    // -------------------------------------------------------------------------

    /** 주문 접수 중 멱등성 충돌 발생 횟수를 증가시킨다. */
    public void incrementIdempotencyConflict() {
        idempotencyConflictCounter.increment();
    }

    /** 엔진 큐 submit 실패 후 보상 롤백 성공 횟수를 증가시킨다. */
    public void incrementQueueFullRollback() {
        queueFullRollbackCounter.increment();
    }

    /** 주문 등록 요청을 받은 횟수를 증가시킨다. */
    public void incrementPlaceOrderTps() {
        placeOrderTpsCounter.increment();
    }

    /** 주문이 승인되어 엔진 큐에 들어간 횟수를 증가시킨다. */
    public void incrementAcceptedOrderTps() {
        acceptedOrderTpsCounter.increment();
    }

    /** 주문 취소 요청을 받은 횟수를 증가시킨다. */
    public void incrementCancelOrderTps() {
        cancelOrderTpsCounter.increment();
    }

    /** 주문 승인 트랜잭션 지연 시간 측정을 위한 타이머를 반환한다. */
    public Timer orderAcceptTxTimer() {
        return orderAcceptTxLatencyTimer;
    }
}
