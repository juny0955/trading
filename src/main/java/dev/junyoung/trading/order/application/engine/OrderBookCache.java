package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.domain.model.OrderBook;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * engine-thread가 생성한 호가창 스냅샷을 HTTP 스레드에 안전하게 노출하는 캐시.
 *
 * <h2>스레드 모델</h2>
 * <pre>
 *   engine-thread                       HTTP thread
 *        |                                   |
 *   update(orderBook)                        |
 *   └─ ref.set(new Snapshot(...))  ──────>  ref.get()
 *      (단일 volatile write)            (단일 volatile read)
 * </pre>
 *
 * <ul>
 *   <li>{@link #update}는 engine-thread 전용. 락 없이 새 스냅샷을 원자적으로 교체한다.</li>
 *   <li>{@link #latestBids}, {@link #latestAsks}는 임의 스레드에서 호출 가능.
 *       단일 {@code ref.get()} 으로 동일 스냅샷에서 두 맵을 꺼내므로 bids/asks 일관성이 보장된다.</li>
 * </ul>
 */
@Component
public class OrderBookCache {

    /**
     * bids/asks를 하나의 참조로 묶어 단일 {@link AtomicReference}로 원자적 교체를 가능하게 하는 홀더.
     * bidsRef/asksRef를 분리하면 두 번의 volatile write 사이에 HTTP 스레드가 읽어
     * 불일치 스냅샷을 받을 수 있으므로 반드시 묶어야 한다.
     */
    private record Snapshot(NavigableMap<Long, Long> bids, NavigableMap<Long, Long> asks) {}

    /**
     * 앱 기동 직후 첫 커맨드가 처리되기 전에 API가 호출될 때 반환되는 초기값.
     * NPE 없이 빈 bids/asks를 반환한다.
     */
    private static final Snapshot EMPTY = new Snapshot(
            Collections.unmodifiableNavigableMap(new TreeMap<Long, Long>(Comparator.reverseOrder())),
            Collections.unmodifiableNavigableMap(new TreeMap<Long, Long>())
    );

    /** 항상 최신 스냅샷을 가리킨다. EMPTY로 초기화되어 null이 반환되지 않음이 보장된다. */
    private final AtomicReference<Snapshot> ref = new AtomicReference<>(EMPTY);

    /**
     * engine-thread에서만 호출. {@link OrderBook}의 현재 상태를 스냅샷으로 빌드해 원자적으로 교체한다.
     *
     * <p>스냅샷 빌드(Price → long 변환 포함)를 engine-thread에서 수행하므로
     * HTTP 읽기 경로에 추가 할당이 없다.</p>
     *
     * <p>반환된 맵은 {@link Collections#unmodifiableNavigableMap}으로 감싸져 있어
     * 외부에서 수정할 수 없다.</p>
     */
    public void update(OrderBook orderBook) {
        NavigableMap<Long, Long> bids = new TreeMap<>(Comparator.reverseOrder());
        orderBook.bidsSnapshot().forEach((p, q) -> bids.put(p.value(), q));

        NavigableMap<Long, Long> asks = new TreeMap<>();
        orderBook.asksSnapshot().forEach((p, q) -> asks.put(p.value(), q));

        ref.set(new Snapshot(
            Collections.unmodifiableNavigableMap(bids),
            Collections.unmodifiableNavigableMap(asks)
        ));
    }

    /** 임의 스레드에서 호출 가능. 최신 스냅샷의 매수 호가 맵을 반환한다. 블로킹 없음. null 반환 없음. */
    public NavigableMap<Long, Long> latestBids() { return ref.get().bids(); }

    /** 임의 스레드에서 호출 가능. 최신 스냅샷의 매도 호가 맵을 반환한다. 블로킹 없음. null 반환 없음. */
    public NavigableMap<Long, Long> latestAsks() { return ref.get().asks(); }
}
