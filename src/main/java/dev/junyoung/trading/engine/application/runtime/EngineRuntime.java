package dev.junyoung.trading.engine.application.runtime;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import dev.junyoung.trading.engine.application.book.OrderBookProjectionApplier;
import dev.junyoung.trading.engine.application.book.OrderBookRebuilder;
import dev.junyoung.trading.engine.application.book.OrderBookSnapshotMapper;
import dev.junyoung.trading.engine.application.book.OrderBookStateApplier;
import dev.junyoung.trading.engine.application.book.SymbolOrderBookStateApplier;
import dev.junyoung.trading.engine.application.exception.EngineNotActiveException;
import dev.junyoung.trading.engine.application.handler.EngineHandler;
import dev.junyoung.trading.engine.application.loop.EngineCommand;
import dev.junyoung.trading.engine.application.loop.EngineLoop;
import dev.junyoung.trading.engine.application.loop.EngineThread;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import dev.junyoung.trading.engine.application.port.out.EngineResultCommitPort;
import dev.junyoung.trading.engine.domain.entity.OrderBook;
import dev.junyoung.trading.engine.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.shared.port.out.OrderBookCachePort;
import lombok.extern.slf4j.Slf4j;

/**
 * 단일 심볼의 매칭 엔진을 구성하는 모든 컴포넌트를 담는 컨테이너.
 *
 * <p>Spring 빈이 아니며 {@link EngineManager}가 직접 생성·소유한다.
 * 생성자에서 {@link BlockingQueue}, {@link OrderBook},
 * {@link EngineThread}, {@link MatchingEngine}, {@link EngineHandler}, {@link EngineLoop}를 조립하므로
 * 각 컴포넌트는 심볼 단위로 완전히 격리된다.</p>
 */
@Slf4j
public class EngineRuntime implements EngineRuntimeOwner {

    private volatile EngineSymbolStatus state = EngineSymbolStatus.REBUILDING;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private static final int QUEUE_CAPACITY = 10_000;

    private final Symbol symbol;
    private final OrderBook orderBook;
    private final EngineLoop engineLoop;
    private final OrderBookCachePort orderBookCachePort;
    private final OrderBookRebuilder orderBookRebuilder;
    private final AtomicLong eventSequence;

    /** 심볼별 큐·스레드·핸들러를 조립하고 {@link EngineLoop}를 초기화한다. */
    public EngineRuntime(
        Symbol symbol,
        OrderBookCachePort orderBookCachePort,
        OrderBookProjectionApplier orderBookProjectionApplier,
        EngineResultCommitPort engineResultCommitPort,
        OrderBookRebuilder orderBookRebuilder,
        EngineMetrics engineMetrics,
        long lastEventSequence
    ) {
        this.symbol = symbol;
        this.orderBook = new OrderBook();
        this.orderBookCachePort = orderBookCachePort;
        this.orderBookRebuilder = orderBookRebuilder;
        this.eventSequence = new AtomicLong(lastEventSequence);
        attemptRebuild();
        BlockingQueue<EngineCommand> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        engineMetrics.registerQueueDepthGauge(symbol.value(), queue);
        EngineThread engineThread = new EngineThread(symbol.value());
        MatchingEngine matchingEngine = new MatchingEngine();
        OrderBookStateApplier orderBookStateApplier = new SymbolOrderBookStateApplier(orderBook, orderBookProjectionApplier);
        EngineHandler engineHandler = new EngineHandler(symbol, matchingEngine, orderBook, orderBookStateApplier, orderBookCachePort, engineResultCommitPort, this, engineMetrics);
        this.engineLoop = new EngineLoop(queue, engineHandler, engineThread, this, engineMetrics);
    }

    // -------------------------------------------------------------------------
    // 진입점
    // -------------------------------------------------------------------------

    /** engine-thread를 시작한다. */
    public void start() { engineLoop.start(); }

    /** 심볼별 단조 증가 event_sequence를 발급한다. */
    public long nextEventSequence() { return eventSequence.incrementAndGet(); }

    /** engine-thread를 중단하고 자원을 반납한다. */
    public void stop() { engineLoop.stop(); }

    /**
     * 커맨드를 엔진 큐에 제출한다.
     *
     * <p><b>2단 방어 구조:</b>
     * <ol>
     *   <li>여기서 {@code state != ACTIVE} 이면 {@link EngineNotActiveException}을 던져 빠르게 거부한다 (1차).</li>
     *   <li>이 검사와 {@link EngineLoop#submit} 사이에
     *       engine-thread가 {@link #transitionToRebuilding()}을 호출하면 커맨드가 큐에 삽입될 수 있다.
     *       이 경우 {@link EngineHandler#handle}
     *       진입 시점의 {@code state != ACTIVE} 가드가 커맨드를 드롭하며 {@code log.warn}을 남긴다 (2차).</li>
     * </ol>
     * 클라이언트가 예외 없이 요청이 드롭될 수 있으나, 정합성은 항상 보장된다.</p>
     */
    public void submit(EngineCommand engineCommand) {
        if (state != EngineSymbolStatus.ACTIVE)
            throw new EngineNotActiveException(state);
        engineLoop.submit(engineCommand);
    }

    @Override
    public EngineSymbolStatus state() {
        return state;
    }

    @Override
    public void transitionToActive() {
        state = EngineSymbolStatus.ACTIVE;
        log.info("[{}] Engine transitioning to ACTIVE", symbol.value());
    }

    @Override
    public void transitionToRebuilding() {
        state = EngineSymbolStatus.REBUILDING;
        log.warn("[{}] Engine transitioning to REBUILDING — rebuild required", symbol.value());
    }

    @Override
    public void transitionToDirty() {
        state = EngineSymbolStatus.DIRTY;
        log.error("[{}] Engine transitioning to DIRTY — manual intervention required", symbol.value());
    }

    @Override
    public void attemptRebuild() {
        try {
            List<Order> openOrders = orderBookRebuilder.loadOpenOrders(symbol);
            orderBook.rebuild(openOrders);
            orderBookCachePort.update(symbol, OrderBookSnapshotMapper.from(orderBook));
            transitionToActive();
        } catch (Exception e) {
            transitionToDirty();
            log.error("[{}] Rebuild failed — engine DIRTY, manual intervention required", symbol.value(), e);
        }
    }
}
