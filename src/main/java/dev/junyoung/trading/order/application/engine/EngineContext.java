package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 단일 심볼의 매칭 엔진을 구성하는 모든 컴포넌트를 담는 컨테이너.
 *
 * <p>Spring 빈이 아니며 {@link EngineManager}가 직접 생성·소유한다.
 * 생성자에서 {@link java.util.concurrent.BlockingQueue}, {@link OrderBook},
 * {@link EngineThread}, {@link MatchingEngine}, {@link EngineHandler}, {@link EngineLoop}를 조립하므로
 * 각 컴포넌트는 심볼 단위로 완전히 격리된다.</p>
 */
public class EngineContext {
    private static final int QUEUE_CAPACITY = 10_000;

    private final EngineLoop engineLoop;

    /** 심볼별 큐·스레드·핸들러를 조립하고 {@link EngineLoop}를 초기화한다. */
    public EngineContext(Symbol symbol, OrderRepository orderRepository, OrderBookCache orderBookCache) {
        BlockingQueue<EngineCommand> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        OrderBook orderBook = new OrderBook();
        EngineThread engineThread = new EngineThread(symbol.value());
        MatchingEngine matchingEngine = new MatchingEngine(orderBook);
        EngineHandler engineHandler = new EngineHandler(symbol, matchingEngine, orderBook, orderBookCache, orderRepository);
        this.engineLoop = new EngineLoop(queue, engineHandler, engineThread);
    }

    /** engine-thread를 시작한다. */
    public void start() { engineLoop.start(); }

    /** engine-thread를 중단하고 자원을 반납한다. */
    public void stop() { engineLoop.stop(); }

    /** 커맨드를 엔진 큐에 제출한다. */
    public void submit(EngineCommand engineCommand) { engineLoop.submit(engineCommand); }
}
