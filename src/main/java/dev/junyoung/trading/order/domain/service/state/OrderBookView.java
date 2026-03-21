package dev.junyoung.trading.order.domain.service.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

/**
 * MatchingEngine 계산 전용 호가창 working copy.
 *
 * <p>live {@code OrderBook}의 구조를 완전 분리 복사한 mutable 상태다.
 * engine 계산 중 이 객체를 변경해도 live {@code OrderBook}에 영향을 주지 않는다.</p>
 *
 * <h3>불변식</h3>
 * <ul>
 *   <li>{@code bids} / {@code asks} — <b>순서 전용</b>: 가격 레벨별 FIFO 위치 마커.
 *       {@code Deque} 내 {@code OrderId}는 순서만 나타내며, 실제 주문 상태는 반드시 {@code index}에서 조회한다.</li>
 *   <li>{@code index} — <b>상태 전용</b>: {@code OrderId → Order} 매핑이 최신 주문 상태의 정규 소스다.</li>
 *   <li>{@code Deque}에 {@code OrderId}가 있어도 {@code index}에 없으면 지연 삭제된 항목으로 간주하며, 예외가 아닌 정상 상태다.</li>
 *   <li>반대로 {@code index}에 객체가 존재하는데 상태(가격·방향)가 큐 위치와 불일치하면 invariant 위반이다.</li>
 * </ul>
 *
 * <p>생성은 {@code OrderBookViewFactory}(application 계층)가 담당한다.</p>
 */
public class OrderBookView {
    private final NavigableMap<Price, Deque<OrderId>> bids;
    private final NavigableMap<Price, Deque<OrderId>> asks;
    private final Map<OrderId, Order> index;

    public OrderBookView(
        NavigableMap<Price, Deque<OrderId>> bids,
        NavigableMap<Price, Deque<OrderId>> asks,
        Map<OrderId, Order> index
    ) {
        this.bids = bids;
        this.asks = asks;
        this.index = index;
    }

    public void add(Order order) {
        bookOf(order.getSide())
            .computeIfAbsent(order.getLimitPriceOrThrow(), _ -> new ArrayDeque<>())
            .addLast(order.getOrderId());
        index.put(order.getOrderId(), order);
    }

    /**
     * 해당 방향의 최우선 주문을 조회한다 (큐에서 제거하지 않음).
     * stale ID는 탐색 과정에서 자동으로 정리된다.
     */
    public Optional<Order> peek(Side side) {
        NavigableMap<Price, Deque<OrderId>> book = bookOf(side);
        while (!book.isEmpty()) {
            Deque<OrderId> queue = book.firstEntry().getValue();
            while (!queue.isEmpty()) {
                OrderId id = queue.peekFirst();
                Order order = index.get(id);
                if (order != null) return Optional.of(order);
                queue.pollFirst(); // lazy-skip: index에 없는 stale id 제거
            }
            book.pollFirstEntry(); // 빈 가격 레벨 제거
        }
        return Optional.empty();
    }

    /**
     * 해당 방향의 최우선 주문을 소비(큐 + index에서 제거)한다.
     * stale ID는 탐색 과정에서 자동으로 정리된다.
     */
    public void poll(Side side) {
        NavigableMap<Price, Deque<OrderId>> book = bookOf(side);
        while (!book.isEmpty()) {
            Deque<OrderId> queue = book.firstEntry().getValue();
            while (!queue.isEmpty()) {
                OrderId id = queue.pollFirst();
                Order order = index.remove(id);
                if (order != null) {
                    removeFirstLevelIfEmpty(book);
                    return; // 실제 주문 소비 완료
                }
                // stale id였으면 계속 탐색
            }
            book.pollFirstEntry(); // 빈 가격 레벨 제거
        }
    }

    public void replaceInIndex(Order order) {
        index.put(order.getOrderId(), order);
    }

    /**
     * FOK 사전 충족성 검사용. makerSide 방향에서 limitPrice 조건을 만족하는 총 잔량을 집계한다.
     * <ul>
     *   <li>SELL maker: asks 오름차순에서 price ≤ limitPrice 인 레벨 합산</li>
     *   <li>BUY  maker: bids 내림차순에서 price ≥ limitPrice 인 레벨 합산</li>
     * </ul>
     */
    public Quantity totalAvailableQty(Side makerSide, Price limitPrice) {
        NavigableMap<Price, Deque<OrderId>> book = bookOf(makerSide);
        return new Quantity(
            book.headMap(limitPrice, true).values().stream()
                .flatMap(Deque::stream)
                .map(index::get)
                .filter(Objects::nonNull)
                .mapToLong(order -> order.getRemaining().value())
                .sum()
        );
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private NavigableMap<Price, Deque<OrderId>> bookOf(Side side) {
        return side.isBuy() ? bids : asks;
    }

    /** 최우선 가격 레벨의 큐가 비었으면 해당 레벨을 제거한다. */
    private void removeFirstLevelIfEmpty(NavigableMap<Price, Deque<OrderId>> book) {
        if (!book.isEmpty() && book.firstEntry().getValue().isEmpty()) {
            book.pollFirstEntry();
        }
    }
}