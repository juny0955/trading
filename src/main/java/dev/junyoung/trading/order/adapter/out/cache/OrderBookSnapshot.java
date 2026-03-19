package dev.junyoung.trading.order.adapter.out.cache;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.Symbol;

import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 호가창의 완전 불변 스냅샷.
 *
 * <p>겉 객체와 내부 컬렉션 모두 불변이므로, engine-thread가 생성한 뒤
 * HTTP 스레드가 동기화 없이 안전하게 읽을 수 있다.</p>
 *
 * <ul>
 *   <li>생성: engine-thread에서 {@link #from(OrderBook)}으로 생성 후 {@link OrderBookCache}에 put.</li>
 *   <li>조회: HTTP 스레드에서 {@link OrderBookCache#getSnapshot(Symbol)}으로 참조를 가져온 뒤
 *       {@link #bids()}, {@link #asks()}를 호출.</li>
 * </ul>
 */
public final class OrderBookSnapshot {

    // -------------------------------------------------------------------------
    // 팩토리 (진입점)
    // -------------------------------------------------------------------------

    /** 앱 기동 직후 또는 미등록 심볼 조회 시 반환되는 빈 스냅샷. NPE 방지용. */
    public static final OrderBookSnapshot EMPTY = new OrderBookSnapshot(
        Collections.unmodifiableNavigableMap(new TreeMap<Long, Long>(Comparator.reverseOrder())),
        Collections.unmodifiableNavigableMap(new TreeMap<Long, Long>())
    );

    private final NavigableMap<Long, Long> bids;
    private final NavigableMap<Long, Long> asks;

    private OrderBookSnapshot(NavigableMap<Long, Long> bids, NavigableMap<Long, Long> asks) {
        this.bids = bids;
        this.asks = asks;
    }

    /**
     * {@link OrderBook}의 현재 상태를 읽어 불변 스냅샷을 생성한다.
     * engine-thread에서만 호출해야 한다.
     */
    public static OrderBookSnapshot from(OrderBook orderBook) {
        NavigableMap<Long, Long> bids = new TreeMap<>(Comparator.reverseOrder());
        orderBook.bidsSnapshot().forEach((p, q) -> bids.put(p.value(), q));

        NavigableMap<Long, Long> asks = new TreeMap<>();
        orderBook.asksSnapshot().forEach((p, q) -> asks.put(p.value(), q));

        return new OrderBookSnapshot(
            Collections.unmodifiableNavigableMap(bids),
            Collections.unmodifiableNavigableMap(asks)
        );
    }

    // -------------------------------------------------------------------------
    // 조회
    // -------------------------------------------------------------------------

    /** 매수 호가 맵 (가격 내림차순). 불변. */
    public NavigableMap<Long, Long> bids() { return bids; }

    /** 매도 호가 맵 (가격 오름차순). 불변. */
    public NavigableMap<Long, Long> asks() { return asks; }
}
