package dev.junyoung.trading.order.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;

/**
 * 엔진 처리 계층의 계측 포인트를 관리한다.
 *
 * <pre>
 * - 취소 명령 처리: cancel_completed_tps, cancel_rejected_tps
 * - 엔진 큐 상태: engine_backpressure_count, engine_queue_depth (gauge)
 * - DB 동시성: balance_lock_contention_count, db_deadlock_count, db_lock_wait_time
 * - DB 트랜잭션: db_commit_failure_count, db_rollback_count
 * - 성능 지표: queue_wait_latency, engine_processing_latency, engine_result_tx_latency
 * - 엔드투엔드 지표: end_to_end_order_latency, end_to_end_cancel_latency
 * - 에러: error_rate
 * </pre>
 */
@Component
public class EngineMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter cancelCompletedCounter;
    private final Counter cancelRejectedCounter;
    private final Counter engineBackpressureCounter;
    private final Counter balanceLockContentionCounter;
    private final Counter dbDeadlockCounter;
    private final Counter dbCommitFailureCounter;
    private final Counter dbRollbackCounter;
    private final Counter errorRateCounter;

    // Timers
    private final Timer queueWaitLatencyTimer;
    private final Timer engineProcessingLatencyTimer;
    private final Timer engineResultTxLatencyTimer;
    private final Timer endToEndOrderLatencyTimer;
    private final Timer endToEndCancelLatencyTimer;
    private final Timer dbLockWaitTimeTimer;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    public EngineMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Counters initialization
        this.cancelCompletedCounter = Counter.builder("cancel_completed_tps")
            .description("Number of cancellations persisted to DB")
            .register(meterRegistry);

        this.cancelRejectedCounter = Counter.builder("cancel_rejected_tps")
            .description("Number of cancellations rejected by engine")
            .register(meterRegistry);

        this.engineBackpressureCounter = Counter.builder("engine_backpressure_count")
            .description("Number of times engine queue was full on submit")
            .register(meterRegistry);

        this.balanceLockContentionCounter = Counter.builder("balance_lock_contention_count")
            .description("Number of SELECT FOR UPDATE calls on balance table")
            .register(meterRegistry);

        this.dbDeadlockCounter = Counter.builder("db_deadlock_count")
            .description("Number of deadlock exceptions detected")
            .register(meterRegistry);

        this.dbCommitFailureCounter = Counter.builder("db_commit_failure_count")
            .description("Number of DB commit failures")
            .register(meterRegistry);

        this.dbRollbackCounter = Counter.builder("db_rollback_count")
            .description("Number of DB transaction rollbacks")
            .register(meterRegistry);

        this.errorRateCounter = Counter.builder("error_rate")
            .description("Total application errors")
            .register(meterRegistry);

        // Timers initialization
        this.queueWaitLatencyTimer = Timer.builder("queue_wait_latency")
            .description("Time from command enqueue to dequeue (queue wait)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.engineProcessingLatencyTimer = Timer.builder("engine_processing_latency")
            .description("Time to process a single engine command")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.engineResultTxLatencyTimer = Timer.builder("engine_result_tx_latency")
            .description("Duration of engine result persistence transaction")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.endToEndOrderLatencyTimer = Timer.builder("end_to_end_order_latency")
            .description("End-to-end order latency from service entry to DB persistence")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.endToEndCancelLatencyTimer = Timer.builder("end_to_end_cancel_latency")
            .description("End-to-end cancel latency from service entry to DB persistence")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.dbLockWaitTimeTimer = Timer.builder("db_lock_wait_time")
            .description("Time spent in balance table SELECT FOR UPDATE calls (approximation)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // 카운터 증가 메서드
    // -------------------------------------------------------------------------

    /** 취소 완료(DB 저장) 횟수를 증가시킨다. */
    public void incrementCancelCompleted() {
        cancelCompletedCounter.increment();
    }

    /** 취소 거부 횟수를 증가시킨다. */
    public void incrementCancelRejected() {
        cancelRejectedCounter.increment();
    }

    /** 엔진 큐 풀(backpressure) 발생 횟수를 증가시킨다. */
    public void incrementEngineBackpressure() {
        engineBackpressureCounter.increment();
    }

    /** 잔액 테이블 SELECT FOR UPDATE 호출 횟수를 증가시킨다. */
    public void incrementBalanceLockContention() {
        balanceLockContentionCounter.increment();
    }

    /** DB 데드락 감지 횟수를 증가시킨다. */
    public void incrementDbDeadlock() {
        dbDeadlockCounter.increment();
    }

    /** DB 커밋 실패 횟수를 증가시킨다. */
    public void incrementDbCommitFailure() {
        dbCommitFailureCounter.increment();
    }

    /** DB 롤백 횟수를 증가시킨다. */
    public void incrementDbRollback() {
        dbRollbackCounter.increment();
    }

    /** 애플리케이션 에러 횟수를 증가시킨다. */
    public void incrementErrorRate() {
        errorRateCounter.increment();
    }

    // -------------------------------------------------------------------------
    // 타이머 기록 메서드
    // -------------------------------------------------------------------------

    /** 큐 대기 지연 시간을 기록한다. */
    public void recordQueueWaitLatency(Duration duration) {
        queueWaitLatencyTimer.record(duration);
    }

    /** 엔진 명령 처리 지연 시간을 기록한다. */
    public void recordEngineProcessingLatency(Duration duration) {
        engineProcessingLatencyTimer.record(duration);
    }

    /** 엔진 결과 트랜잭션 지연 시간을 기록한다. */
    public void recordEngineResultTxLatency(Duration duration) {
        engineResultTxLatencyTimer.record(duration);
    }

    /** 주문 엔드투엔드 지연 시간을 기록한다. */
    public void recordEndToEndOrderLatency(Duration duration) {
        endToEndOrderLatencyTimer.record(duration);
    }

    /** 취소 엔드투엔드 지연 시간을 기록한다. */
    public void recordEndToEndCancelLatency(Duration duration) {
        endToEndCancelLatencyTimer.record(duration);
    }

    /** DB 잠금 대기 시간을 기록한다. */
    public void recordDbLockWaitTime(Duration duration) {
        dbLockWaitTimeTimer.record(duration);
    }

    // -------------------------------------------------------------------------
    // 동적 게이지 등록
    // -------------------------------------------------------------------------

    /**
     * 심볼별 엔진 큐 깊이 게이지를 등록한다.
     * 스타트업 시 각 심볼마다 한 번씩 호출해야 한다.
     *
     * @param symbol 심볼
     * @param queue 큐
     */
    public void registerQueueDepthGauge(String symbol, BlockingQueue<?> queue) {
        Gauge.builder("engine_queue_depth", queue::size)
            .description("Engine queue depth")
            .tag("symbol", symbol)
            .register(meterRegistry);
    }
}
