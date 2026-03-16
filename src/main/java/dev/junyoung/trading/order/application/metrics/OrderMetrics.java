package dev.junyoung.trading.order.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
}
