package dev.junyoung.trading.engine.application.metrics;

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
    private final Counter rejectedOrderTpsCounter;
    private final Counter tradesPerSecCounter;
    private final Counter matchedOrdersPerSecCounter;

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

        this.rejectedOrderTpsCounter = Counter.builder("rejected_order_tps")
            .description("Number of place orders rejected by engine calculation")
            .register(meterRegistry);

        this.tradesPerSecCounter = Counter.builder("trades_per_sec")
            .description("Number of trades persisted to DB")
            .register(meterRegistry);

        this.matchedOrdersPerSecCounter = Counter.builder("matched_orders_per_sec")
            .description("Number of orders (taker + makers) involved in at least one trade")
            .register(meterRegistry);

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

    public void incrementCancelCompleted() { cancelCompletedCounter.increment(); }
    public void incrementCancelRejected() { cancelRejectedCounter.increment(); }
    public void incrementEngineBackpressure() { engineBackpressureCounter.increment(); }
    public void incrementBalanceLockContention() { balanceLockContentionCounter.increment(); }
    public void incrementDbDeadlock() { dbDeadlockCounter.increment(); }
    public void incrementDbCommitFailure() { dbCommitFailureCounter.increment(); }
    public void incrementDbRollback() { dbRollbackCounter.increment(); }
    public void incrementErrorRate() { errorRateCounter.increment(); }
    public void incrementRejectedOrderTps() { rejectedOrderTpsCounter.increment(); }
    public void incrementTradesPerSec(int count) { tradesPerSecCounter.increment(count); }
    public void incrementMatchedOrdersPerSec(int count) { matchedOrdersPerSecCounter.increment(count); }

    // -------------------------------------------------------------------------
    // 타이머 기록 메서드
    // -------------------------------------------------------------------------

    public void recordQueueWaitLatency(Duration duration) { queueWaitLatencyTimer.record(duration); }
    public void recordEngineProcessingLatency(Duration duration) { engineProcessingLatencyTimer.record(duration); }
    public void recordEngineResultTxLatency(Duration duration) { engineResultTxLatencyTimer.record(duration); }
    public void recordEndToEndOrderLatency(Duration duration) { endToEndOrderLatencyTimer.record(duration); }
    public void recordEndToEndCancelLatency(Duration duration) { endToEndCancelLatencyTimer.record(duration); }
    public void recordDbLockWaitTime(Duration duration) { dbLockWaitTimeTimer.record(duration); }

    // -------------------------------------------------------------------------
    // 동적 게이지 등록
    // -------------------------------------------------------------------------

    public void registerQueueDepthGauge(String symbol, BlockingQueue<?> queue) {
        Gauge.builder("engine_queue_depth", queue, BlockingQueue::size)
            .description("Engine queue depth")
            .tag("symbol", symbol)
            .register(meterRegistry);
    }
}
