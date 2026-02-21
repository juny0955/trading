package dev.junyoung.trading.order.domain.service;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.enums.Side;

/**
 * 단일 종목 호가창. bids(매수 내림차순) / asks(매도 오름차순), 동일 가격 FIFO.
 * 체결 로직은 포함하지 않는다 — 매칭은 {@code MatchingEngine}이 담당한다.
 */
public class OrderBook {

	/** 매수: 높은 가격 우선 */
	private final NavigableMap<Price, Deque<Order>> bids = new TreeMap<>(Comparator.comparing(Price::value).reversed());

	/** 매도: 낮은 가격 우선 */
	private final NavigableMap<Price, Deque<Order>> asks = new TreeMap<>(Comparator.comparing(Price::value));

	/** O(1) 취소 조회용 역방향 인덱스 */
	private final Map<OrderId, Order> index = new HashMap<>();

	/**
	 * 주문을 호가창에 등록한다. 동일 가격 레벨이 있으면 큐 맨 뒤에 추가(FIFO).
	 *
	 * @param order 등록할 주문 ({@link OrderStatus#NEW} 또는 {@link OrderStatus#PARTIALLY_FILLED})
	 */
	public void add(Order order) {
		NavigableMap<Price, Deque<Order>> book = bookOf(order.getSide());
		book.computeIfAbsent(order.getPrice(), _ -> new ArrayDeque<>())
			.addLast(order);

		index.put(order.getOrderId(), order);
	}

	/**
	 * 최우선 호가 주문을 꺼낸다. 레벨이 비면 레벨 자체도 제거하고 index도 정리한다.
	 *
	 * @param side {@link Side#BUY} → 최고 매수가, {@link Side#SELL} → 최저 매도가
	 * @return 최우선 주문. 호가 없으면 {@link Optional#empty()}
	 */
	public Optional<Order> poll(Side side) {
		Map.Entry<Price, Deque<Order>> bestLevel = bestLevelOf(side);
		if (bestLevel == null) return Optional.empty();

		Order order = bestLevel.getValue().pollFirst();
		if (order == null) return Optional.empty();

		removeEmptyLevel(bookOf(side), bestLevel);
		index.remove(order.getOrderId());

		return Optional.of(order);
	}

	/**
	 * @return 최우선 매수 호가(Best Bid). 없으면 {@link Optional#empty()}
	 */
	public Optional<Price> bestBid() {
		return firstKeyOf(bids);
	}

	/**
	 * @return 최우선 매도 호가(Best Ask). 없으면 {@link Optional#empty()}
	 */
	public Optional<Price> bestAsk() {
		return firstKeyOf(asks);
	}

	/**
	 * 주문을 호가창에서 제거한다(취소용). 레벨이 비면 레벨도 제거한다.
	 * <p><b>O(n)</b> — 레벨 내 선형 탐색. 추후 고성능 필요 시 linked-list + node 참조로 교체 필요.
	 *
	 * @param orderId 제거할 주문 ID
	 * @return 제거된 주문. 존재하지 않거나 이미 체결된 경우 {@link Optional#empty()}
	 */
	public Optional<Order> remove(OrderId orderId) {
		Order order = index.remove(orderId);
		if (order == null) return Optional.empty();

		NavigableMap<Price, Deque<Order>> book = bookOf(order.getSide());
		Deque<Order> queue = book.get(order.getPrice());
		if (queue == null) return Optional.empty();

		queue.remove(order);
		if (queue.isEmpty()) book.remove(order.getPrice());

		return Optional.of(order);
	}

	private NavigableMap<Price, Deque<Order>> bookOf(Side side) {
		return side == Side.BUY ? bids : asks;
	}

	private Map.Entry<Price, Deque<Order>> bestLevelOf(Side side) {
		return bookOf(side).firstEntry();
	}

	private void removeEmptyLevel(
		NavigableMap<Price, Deque<Order>> book,
		Map.Entry<Price, Deque<Order>> level
	) {
		if (level.getValue().isEmpty()) book.remove(level.getKey());
	}

	private Optional<Price> firstKeyOf(NavigableMap<Price, Deque<Order>> book) {
		return book.isEmpty() ? Optional.empty() : Optional.of(book.firstKey());
	}
}