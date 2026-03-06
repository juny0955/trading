package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * engine-thread가 생성한 호가창 스냅샷을 HTTP 스레드에 안전하게 노출하는 캐시.
 *
 * <h2>스레드 모델</h2>
 * <pre>
 *   engine-thread                      HTTP thread
 *        |                                  |
 *   update(symbol, orderBook)               |
 *   └─ snapshot = OrderBookSnapshot.from()  |
 *      cache.put(symbol, snapshot) ────> cache.getOrDefault(symbol, EMPTY)
 *      (ConcurrentHashMap 원자적 write)      (ConcurrentHashMap 원자적 read)
 * </pre>
 *
 * <ul>
 *   <li>{@link #update}는 engine-thread 전용. 해당 symbol 스냅샷만 교체하며 다른 symbol에 영향을 주지 않는다.</li>
 *   <li>{@link #getSnapshot}은 임의 스레드에서 호출 가능. bids/asks가 동일 스냅샷에서 나오므로 일관성이 보장된다.</li>
 *   <li>{@link OrderBookSnapshot}이 완전 불변이므로, put/get 사이 추가 동기화가 불필요하다.</li>
 * </ul>
 */
@Component
public class OrderBookCache {

    private final ConcurrentHashMap<Symbol, OrderBookSnapshot> cache = new ConcurrentHashMap<>();

    /**
     * engine-thread에서만 호출. {@link OrderBook}으로부터 새 스냅샷을 생성해 해당 심볼 캐시를 교체한다.
     */
    protected void update(Symbol symbol, OrderBook orderBook) {
        cache.put(symbol, OrderBookSnapshot.from(orderBook));
    }

    /**
     * 임의 스레드에서 호출 가능. 해당 심볼의 최신 스냅샷을 반환한다.
     * 등록되지 않은 심볼이면 {@link OrderBookSnapshot#EMPTY}를 반환한다 (NPE 없음).
     */
    public OrderBookSnapshot getSnapshot(Symbol symbol) {
        return cache.getOrDefault(symbol, OrderBookSnapshot.EMPTY);
    }
}
