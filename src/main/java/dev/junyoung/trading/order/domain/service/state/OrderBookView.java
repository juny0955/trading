package dev.junyoung.trading.order.domain.service.state;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;

import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;

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
 * <p>생성은 {@code OrderBookViewFactory}(application 계층)가 담당한다.
 * Spring 비의존 POJO로 유지한다.</p>
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
}
