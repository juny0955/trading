package dev.junyoung.trading.engine.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 스타트업 재생/복구 계층의 계측 포인트를 관리한다.
 *
 * <pre>
 * - 전체 재생 시간: replay_duration_on_startup
 * - 심볼별 재생 시간: replay_duration_by_symbol
 * - ACCEPTED orphan 정리 수: replay_orphan_cancelled_count
 * - 복원된 open order 수: replay_restored_order_count
 * - 정합성 검사: replay_consistency_check (심볼별 게이지)
 * </pre>
 */
@Component
public class ReplayMetrics {

    private final MeterRegistry meterRegistry;

    private final Timer totalReplayDurationTimer;
    private final Counter replayOrphanCancelledCounter;
    private final Counter replayRestoredOrderCounter;
    private final ConcurrentHashMap<String, Timer> replayDurationBySymbolMap;
    private final ConcurrentHashMap<String, AtomicInteger> consistencyCheckMap;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    public ReplayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.replayDurationBySymbolMap = new ConcurrentHashMap<>();
        this.consistencyCheckMap = new ConcurrentHashMap<>();

        this.totalReplayDurationTimer = Timer.builder("replay_duration_on_startup")
            .description("Total startup replay/recovery duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        this.replayOrphanCancelledCounter = Counter.builder("replay_orphan_cancelled_count")
            .description("Number of ACCEPTED orphan orders cancelled during startup recovery")
            .register(meterRegistry);

        this.replayRestoredOrderCounter = Counter.builder("replay_restored_order_count")
            .description("Number of open orders restored to the order book during startup recovery")
            .register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // 타이머 기록 메서드
    // -------------------------------------------------------------------------

    public void recordTotalReplayDuration(Duration duration) {
        totalReplayDurationTimer.record(duration);
    }

    public void recordReplayDurationBySymbol(String symbol, Duration duration) {
        Timer timer = replayDurationBySymbolMap.computeIfAbsent(symbol, _ ->
            Timer.builder("replay_duration_by_symbol")
                .description("Replay/recovery duration by symbol")
                .tag("symbol", symbol)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry)
        );
        timer.record(duration);
    }

    // -------------------------------------------------------------------------
    // 카운터 증가 메서드
    // -------------------------------------------------------------------------

    public void incrementOrphanCancelledCount(int count) {
        replayOrphanCancelledCounter.increment(count);
    }

    public void incrementRestoredOrderCount(int count) {
        replayRestoredOrderCounter.increment(count);
    }

    // -------------------------------------------------------------------------
    // 정합성 검사 게이지
    // -------------------------------------------------------------------------

    public void updateConsistencyCheck(String symbol, int value) {
        AtomicInteger consistencyValue = consistencyCheckMap.computeIfAbsent(symbol, _ -> {
            AtomicInteger atomicInt = new AtomicInteger(value);
            Gauge.builder("replay_consistency_check", atomicInt::get)
                .description("Replay consistency check (1=clean/no orphans, 0=has orphans)")
                .tag("symbol", symbol)
                .register(meterRegistry);
            return atomicInt;
        });
        consistencyValue.set(value);
    }
}
