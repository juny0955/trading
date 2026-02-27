package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.config.TradingProperties;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 심볼별 {@link EngineContext}를 생성·관리하고 커맨드를 올바른 엔진으로 라우팅하는 오케스트레이터.
 *
 * <p>{@code trading.symbols} 프로퍼티에 등록된 심볼마다 독립적인 {@link EngineContext}를 생성한다.
 * {@code contexts}는 {@link PostConstruct} 단계에서 한 번 채워진 후 읽기 전용으로
 * 사용되므로 {@link HashMap}으로 충분하다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineManager {

    private final TradingProperties tradingProperties;
    private final OrderRepository orderRepository;
    private final OrderBookCache orderBookCache;

    private final Map<Symbol, EngineContext> contexts = new HashMap<>();

    /** trading.symbols에 정의된 각 심볼의 EngineContext를 생성하고 엔진 스레드를 시작한다. */
    @PostConstruct
    public void start() {
        for (String sym : tradingProperties.getSymbols()) {
            Symbol symbol = new Symbol(sym);
            EngineContext ctx = new EngineContext(symbol, orderRepository, orderBookCache);
            contexts.put(symbol, ctx);
            ctx.start();
            log.info("Engine started for symbol: {}", symbol.value());
        }
    }

    /** 모든 심볼의 엔진을 순차적으로 중단한다. 개별 엔진 종료 실패는 로그 후 계속 진행한다. */
    @PreDestroy
    public void stop() {
        for (EngineContext ctx : contexts.values()) {
            try {
                ctx.stop();
            } catch (Exception e) {
                log.error("Engine stop failed", e);
            }
        }
    }

    /**
     * 커맨드를 해당 심볼의 엔진 큐에 위임한다.
     *
     * @throws IllegalArgumentException 등록되지 않은 심볼인 경우
     */
    public void submit(Symbol symbol, EngineCommand command) {
        EngineContext ctx = contexts.get(symbol);
        if (ctx == null) throw new IllegalArgumentException(symbol + " not found");
        ctx.submit(command);
    }
}
