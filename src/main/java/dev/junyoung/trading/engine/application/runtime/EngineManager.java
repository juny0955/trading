package dev.junyoung.trading.engine.application.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.engine.application.book.OrderBookProjectionApplier;
import dev.junyoung.trading.engine.application.book.OrderBookRebuilder;
import dev.junyoung.trading.engine.application.contract.CancelCommandEnvelope;
import dev.junyoung.trading.engine.application.contract.PlaceCommandEnvelope;
import dev.junyoung.trading.engine.application.loop.EngineCommand;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import dev.junyoung.trading.engine.application.metrics.ReplayMetrics;
import dev.junyoung.trading.engine.application.port.out.EngineResultCommitPort;
import dev.junyoung.trading.engine.application.port.out.EngineSymbolStateRepository;
import dev.junyoung.trading.engine.application.service.EngineStartupRecoveryService;
import dev.junyoung.trading.engine.domain.model.EngineSymbolState;
import dev.junyoung.trading.order.application.exception.UnsupportedSymbolException;
import dev.junyoung.trading.order.application.port.out.EngineCommandPort;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.shared.port.out.OrderBookCachePort;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 심볼별 {@link EngineRuntime}를 생성·관리하고 커맨드를 올바른 엔진으로 라우팅하는 오케스트레이터.
 *
 * <p>DB의 {@code symbols.status = ACTIVE} 심볼 집합을 기준으로 독립적인 {@link EngineRuntime}를 생성한다.
 * {@code runtimes}는 {@link PostConstruct} 단계에서 한 번 채워진 후 읽기 전용으로
 * 사용되므로 {@link HashMap}으로 충분하다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineManager implements EngineCommandPort {

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private final EngineSymbolStateRepository engineSymbolStateRepository;
    private final EngineStartupRecoveryService engineStartupRecoveryService;
    private final OrderBookCachePort orderBookCachePort;
    private final EngineResultCommitPort engineResultCommitPort;
    private final OrderBookProjectionApplier orderBookProjectionApplier;
    private final OrderBookRebuilder orderBookRebuilder;
    private final EngineMetrics engineMetrics;
    private final ReplayMetrics replayMetrics;

    private final Map<Symbol, EngineRuntime> runtimes = new HashMap<>();

    // -------------------------------------------------------------------------
    // 생명주기
    // -------------------------------------------------------------------------

    /** DB의 ACTIVE 심볼 집합을 기준으로 각 심볼의 EngineRuntime을 생성하고 엔진 스레드를 시작한다. */
    @PostConstruct
    public void start() {
        Instant totalStart = Instant.now();
        List<EngineSymbolState> activeSymbols = engineSymbolStateRepository.findActiveSymbols();
        for (EngineSymbolState engineSymbolState : activeSymbols) {
            Instant symStart = Instant.now();

            Symbol symbol = engineSymbolState.symbol();
            engineStartupRecoveryService.cleanupOrphanAccepted(symbol);

            EngineRuntime runtime = createEngineRuntime(symbol, engineSymbolState.lastEventSequence());
            recordReplayDuration(symbol, symStart);

            runtimes.put(symbol, runtime);
            runtime.start();
            log.info("Engine started for symbol: {}", symbol.value());
        }

        recordTotalReplayDuration(totalStart);
    }

    /** 모든 심볼의 엔진을 순차적으로 중단한다. 개별 엔진 종료 실패는 로그 후 계속 진행한다. */
    @PreDestroy
    public void stop() {
        for (EngineRuntime ctx : runtimes.values()) {
            try {
                ctx.stop();
            } catch (Exception e) {
                log.error("Engine stop failed", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 진입점
    // -------------------------------------------------------------------------

    /**
     * 주문 커맨드를 해당 심볼의 엔진 큐에 위임한다.
     *
     * @throws UnsupportedSymbolException 등록되지 않은 심볼인 경우
     */
    public void submitPlace(PlaceCommandEnvelope command, Instant serviceEnteredAt) {
        EngineRuntime runtime = getEngineRuntime(command.symbol());
        runtime.submit(new EngineCommand.PlaceOrder(command, serviceEnteredAt, null));
    }

    /**
     * 취소 커맨드를 해당 심볼의 엔진 큐에 위임한다.
     *
     * @throws UnsupportedSymbolException 등록되지 않은 심볼인 경우
     */
    public void submitCancel(CancelCommandEnvelope command, Instant serviceEnteredAt) {
        EngineRuntime runtime = getEngineRuntime(command.symbol());
        runtime.submit(new EngineCommand.CancelOrder(command, serviceEnteredAt, null));
    }

    private EngineRuntime createEngineRuntime(Symbol symbol, long lastEventSequence) {
        return new EngineRuntime(
            symbol,
            orderBookCachePort,
            orderBookProjectionApplier,
            engineResultCommitPort,
            orderBookRebuilder,
            engineMetrics,
            lastEventSequence
        );
    }

    private EngineRuntime getEngineRuntime(Symbol symbol) {
        EngineRuntime runtime = runtimes.get(symbol);
        if (runtime == null) throw new UnsupportedSymbolException(symbol.value());
        return runtime;
    }

    private void recordReplayDuration(Symbol symbol, Instant start) {
        replayMetrics.recordReplayDurationBySymbol(symbol.value(), Duration.between(start, Instant.now()));
    }

    private void recordTotalReplayDuration(Instant start) {
        replayMetrics.recordTotalReplayDuration(Duration.between(start, Instant.now()));
    }
}
