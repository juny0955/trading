package dev.junyoung.trading.order.application.metrics;

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
 * - 고아 주문 정리: replay_open_order_count
 * - 정합성 검사: replay_consistency_check (심볼별 게이지)
 * </pre>
 */
@Component
public class ReplayMetrics {

    private final MeterRegistry meterRegistry;

    // Timer for total replay duration
    private final Timer totalReplayDurationTimer;

    // Counter for orphan orders
    private final Counter replayOpenOrderCounter;

    // Per-symbol timers: symbol -> Timer
    private final ConcurrentHashMap<String, Timer> replayDurationBySymbolMap;

    // Per-symbol consistency gauges: symbol -> AtomicInteger
    private final ConcurrentHashMap<String, AtomicInteger> consistencyCheckMap;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    public ReplayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.replayDurationBySymbolMap = new ConcurrentHashMap<>();
        this.consistencyCheckMap = new ConcurrentHashMap<>();

        // Total replay duration timer initialization
        this.totalReplayDurationTimer = Timer.builder("replay_duration_on_startup")
            .description("Total startup replay/recovery duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);

        // Replay open order counter initialization
        this.replayOpenOrderCounter = Counter.builder("replay_open_order_count")
            .description("Total number of orphan orders cleaned up during replay")
            .register(meterRegistry);
    }

    // -------------------------------------------------------------------------
    // 타이머 기록 메서드
    // -------------------------------------------------------------------------

    /** 전체 재생 지연 시간을 기록한다. */
    public void recordTotalReplayDuration(Duration duration) {
        totalReplayDurationTimer.record(duration);
    }

    /**
     * 심볼별 재생 지연 시간을 기록한다.
     * 처음 호출될 때 해당 심볼에 대한 Timer가 동적으로 생성되어 등록된다.
     *
     * @param symbol 심볼
     * @param duration 재생 지연 시간
     */
    public void recordReplayDurationBySymbol(String symbol, Duration duration) {
        Timer timer = replayDurationBySymbolMap.computeIfAbsent(symbol, key ->
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

    /**
     * 재생 중 정리된 고아 주문 개수를 증가시킨다.
     *
     * @param count 정리된 고아 주문 개수
     */
    public void incrementReplayOpenOrderCount(int count) {
        replayOpenOrderCounter.increment(count);
    }

    // -------------------------------------------------------------------------
    // 정합성 검사 게이지
    // -------------------------------------------------------------------------

    /**
     * 심볼별 정합성 검사 값을 업데이트한다.
     * 처음 호출될 때 해당 심볼에 대한 AtomicInteger 게이지가 동적으로 생성되어 등록된다.
     *
     * <p>
     * 같은 symbol에 대한 동시 호출은 순서가 보장되지 않으며, 마지막으로 set된 값이 저장된다.
     * 현재 사용처(startup recovery)에서는 symbol별로 순차 호출되므로 문제없다.
     * </p>
     *
     * @param symbol 심볼
     * @param value 1=정상/고아없음, 0=고아있음
     */
    public void updateConsistencyCheck(String symbol, int value) {
        AtomicInteger consistencyValue = consistencyCheckMap.computeIfAbsent(symbol, key -> {
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
