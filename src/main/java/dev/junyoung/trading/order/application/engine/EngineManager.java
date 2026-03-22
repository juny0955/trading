package dev.junyoung.trading.order.application.engine;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.common.props.TradingProperties;
import dev.junyoung.trading.order.application.exception.order.UnsupportedSymbolException;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 심볼별 {@link EngineRuntime}를 생성·관리하고 커맨드를 올바른 엔진으로 라우팅하는 오케스트레이터.
 *
 * <p>{@code trading.symbols} 프로퍼티에 등록된 심볼마다 독립적인 {@link EngineRuntime}를 생성한다.
 * {@code contexts}는 {@link PostConstruct} 단계에서 한 번 채워진 후 읽기 전용으로
 * 사용되므로 {@link HashMap}으로 충분하다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineManager {

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private final TradingProperties tradingProperties;
    private final OrderBookCachePort orderBookCachePort;
    private final EngineResultPersistenceService engineResultPersistenceService;
    private final OrderBookProjectionApplier orderBookProjectionApplier;
    private final OrderBookRebuilder orderBookRebuilder;

    private final Map<Symbol, EngineRuntime> contexts = new HashMap<>();

    // -------------------------------------------------------------------------
    // 생명주기
    // -------------------------------------------------------------------------

    /** trading.symbols에 정의된 각 심볼의 EngineContext를 생성하고 엔진 스레드를 시작한다. */
    @PostConstruct
    public void start() {
        for (String sym : tradingProperties.getSymbols()) {
            Symbol symbol = new Symbol(sym);
            EngineRuntime ctx = new EngineRuntime(symbol, orderBookCachePort, orderBookProjectionApplier, engineResultPersistenceService, orderBookRebuilder);
            contexts.put(symbol, ctx);
            ctx.start();
            log.info("Engine started for symbol: {}", symbol.value());
        }
    }

    /** 모든 심볼의 엔진을 순차적으로 중단한다. 개별 엔진 종료 실패는 로그 후 계속 진행한다. */
    @PreDestroy
    public void stop() {
        for (EngineRuntime ctx : contexts.values()) {
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
     * 커맨드를 해당 심볼의 엔진 큐에 위임한다.
     *
     * @throws UnsupportedSymbolException 등록되지 않은 심볼인 경우
     */
    public void submit(Symbol symbol, EngineCommand command) {
        EngineRuntime ctx = contexts.get(symbol);
        if (ctx == null) throw new UnsupportedSymbolException(symbol.value());
        ctx.submit(command);
    }
}
