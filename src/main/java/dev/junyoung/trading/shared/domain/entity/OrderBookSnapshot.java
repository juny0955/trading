package dev.junyoung.trading.shared.domain.entity;

import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

import dev.junyoung.trading.shared.adapter.out.cache.OrderBookCache;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.Symbol;

/**
 * 호가창의 완전 불변 스냅샷.
 *
 * <p>겉 객체와 내부 컬렉션 모두 불변이므로, engine-thread가 생성한 뒤
 * HTTP 스레드가 동기화 없이 안전하게 읽을 수 있다.</p>
 *
 * <ul>
 *   <li>생성: engine-thread에서 생성 후 {@link OrderBookCache}에 put.</li>
 *   <li>조회: HTTP 스레드에서 {@link OrderBookCache#getSnapshot(Symbol)}으로 참조를 가져온 뒤
 *       {@link #bids()}, {@link #asks()}를 호출.</li>
 * </ul>
 */
public record OrderBookSnapshot(NavigableMap<Price, Quantity> bids, NavigableMap<Price, Quantity> asks) {

    // -------------------------------------------------------------------------
    // 팩토리 (진입점)
    // -------------------------------------------------------------------------

    /** 앱 기동 직후 또는 미등록 심볼 조회 시 반환되는 빈 스냅샷. NPE 방지용. */
    public static final OrderBookSnapshot EMPTY = new OrderBookSnapshot(
        Collections.unmodifiableNavigableMap(new TreeMap<>(Comparator.comparing(Price::value).reversed())),
        Collections.unmodifiableNavigableMap(new TreeMap<>(Comparator.comparing(Price::value)))
    );
}
