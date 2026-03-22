package dev.junyoung.trading.order.domain.model;

import java.util.*;

import dev.junyoung.trading.order.domain.exception.OrderBookInvariantViolationException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import lombok.Getter;

/**
 * 단일 종목 호가창. bids(매수 내림차순) / asks(매도 오름차순), 동일 가격 FIFO.
 * 체결 로직은 포함하지 않는다 — 매칭은 {@code MatchingEngine}이 담당한다.
 *
 * <h3>내부 자료구조 불변식</h3>
 * <ul>
 *   <li>{@code Deque<OrderId>} (queue): 순서 전용 FIFO 위치 마커.
 *       상태(가격·방향·잔량) 조회는 반드시 {@code index}를 통해 수행한다.</li>
 *   <li>{@code Map<OrderId, Order> index}: 상태 전용. 살아있는 주문의 최신 {@code Order} 인스턴스가 존재하는 유일한 정규 소스.</li>
 *   <li>Lazy Removal 정상 상태: queue에 {@code OrderId}가 남아 있어도 {@code index}에 해당 항목이 없으면
 *       지연 삭제(stale)된 정상 상태이며, {@code peek()}/{@code poll()} 시점에 조용히 제거된다.</li>
 *   <li>Invariant 예외 기준: {@code index}에 객체가 존재하는데 해당 객체의 상태(가격·방향·resting 여부)가
 *       queue 내 위치와 불일치하는 경우에만 {@link OrderBookInvariantViolationException}을 던진다.</li>
 * </ul>
 */
@Getter
public class OrderBook {

	/** 매수: 높은 가격 우선 */
	private final NavigableMap<Price, Deque<OrderId>> bids = new TreeMap<>(Comparator.comparing(Price::value).reversed());

	/** 매도: 낮은 가격 우선 */
	private final NavigableMap<Price, Deque<OrderId>> asks = new TreeMap<>(Comparator.comparing(Price::value));

	/** O(1) 취소 조회용 역방향 인덱스 */
	private final Map<OrderId, Order> index = new HashMap<>();

	// -------------------------------------------------------------------------
	// 조회
	// -------------------------------------------------------------------------

	/**
	 * 최우선 호가 주문을 확인한다
	 *
	 * @param side {@link Side#BUY} -> 최고 매수가, {@link Side#SELL} -> 최저 매도가
	 * @return 최우선 주문. 호가 없으면 {@link Optional#empty()}
	 */
	public Optional<Order> peek(Side side) {
		return findBest(side, false);
	}

	/**
	 * @return 최우선 매수 호가(Best Bid). 없으면 {@link Optional#empty()}
	 */
	public Optional<Price> bestBid() {
		return peek(Side.BUY).map(Order::getLimitPriceOrThrow);
	}

	/**
	 * @return 최우선 매도 호가(Best Ask). 없으면 {@link Optional#empty()}
	 */
	public Optional<Price> bestAsk() {
		return peek(Side.SELL).map(Order::getLimitPriceOrThrow);
	}

	/** 매수 호가창 스냅샷. 가격 → 잔량 합계 (내림차순) */
	public NavigableMap<Price, Quantity> bidsSnapshot() {
		return aggregateDepth(bids);
	}

	/** 매도 호가창 스냅샷. 가격 → 잔량 합계 (오름차순) */
	public NavigableMap<Price, Quantity> asksSnapshot() {
		return aggregateDepth(asks);
	}

	// -------------------------------------------------------------------------
	// 변경
	// -------------------------------------------------------------------------

	/**
	 * FIFO 위치를 유지한 채 {@code index}의 {@code Order} 인스턴스만 새 객체로 교체한다.
	 * 부분 체결 후 잔량 반영 등 상태 갱신에 사용된다.
	 *
	 * <p>다음 조건을 모두 충족해야 한다(위반 시 시스템 invariant 예외):
	 * <ol>
	 *   <li>기존 주문이 {@code index}에 존재할 것 (null 이면 예외)</li>
	 *   <li>기존 주문 및 갱신 주문 모두 지정가(limit) 주문일 것</li>
	 *   <li>기존 주문이 최종 상태(final)가 아닐 것</li>
	 *   <li>갱신 주문이 활성(active) 상태일 것</li>
	 *   <li>사이드(side)가 동일할 것</li>
	 *   <li>종목(symbol)이 동일할 것</li>
	 *   <li>지정가(price)가 동일할 것</li>
	 * </ol>
	 *
	 * <p>이 검증들은 비즈니스 거절이 아닌 시스템 불변식 위반을 의미한다.
	 * 호출 전 상위 레이어에서 이미 정당한 주문임을 보장해야 한다.
	 *
	 * @param updatedOrder 교체할 새 {@code Order} 인스턴스
	 * @throws OrderBookInvariantViolationException 불변식 위반 시
	 */
	public void replaceOrder(Order updatedOrder) {
		Order existing = index.get(updatedOrder.getOrderId());
		if (existing == null)
			throw new OrderBookInvariantViolationException("order not in book: " + updatedOrder.getOrderId());

		if (!updatedOrder.isActive())
			throw new OrderBookInvariantViolationException("updated order is not active: " + updatedOrder.getOrderId());

		if (existing.isMarket() || updatedOrder.isMarket())
			throw new OrderBookInvariantViolationException("order is market: " + updatedOrder.getOrderId());
		if (existing.isFinal())
			throw new OrderBookInvariantViolationException("order already final: " + updatedOrder.getOrderId());
		if (!existing.getSide().equals(updatedOrder.getSide()))
			throw new OrderBookInvariantViolationException("side mismatch");
		if (!existing.getSymbol().equals(updatedOrder.getSymbol()))
			throw new OrderBookInvariantViolationException("symbol mismatch");
		if (!existing.getLimitPriceOrThrow().equals(updatedOrder.getLimitPriceOrThrow()))
			throw new OrderBookInvariantViolationException("price mismatch");

		index.put(updatedOrder.getOrderId(), updatedOrder);
	}

	/**
	 * 주문을 호가창에 등록한다. 동일 가격 레벨이 있으면 큐 맨 뒤에 추가(FIFO).
	 *
	 * <p>다음 조건을 모두 충족해야 한다(위반 시 invariant 예외):
	 * <ul>
	 *   <li>동일 {@code orderId}가 이미 {@code index}에 존재하면 중복 등록 예외</li>
	 *   <li>시장가(market) 주문은 호가창에 등록 불가</li>
	 *   <li>비활성(inactive) 주문은 호가창에 등록 불가</li>
	 * </ul>
	 *
	 * @param order 등록할 주문 ({@link OrderStatus#NEW} 또는
	 *              {@link OrderStatus#PARTIALLY_FILLED})
	 * @throws OrderBookInvariantViolationException 불변식 위반 시
	 */
	public void add(Order order) {
		if (index.containsKey(order.getOrderId()))
			throw new OrderBookInvariantViolationException("duplicated order: " + order.getOrderId());
		if (order.isMarket())
			throw new OrderBookInvariantViolationException("market order cannot be added: " + order.getOrderId());
		if (!order.isActive())
			throw new OrderBookInvariantViolationException("inactive order cannot be added: " + order.getOrderId());

		Price price = order.getLimitPriceOrThrow();
		NavigableMap<Price, Deque<OrderId>> book = bookOf(order.getSide());

		book.computeIfAbsent(price, _ -> new ArrayDeque<>())
			.addLast(order.getOrderId());

		index.put(order.getOrderId(), order);
	}

	/**
	 * 최우선 호가 주문을 꺼낸다.
	 *
	 * <p>queue에서 꺼낸 {@code OrderId}가 {@code index}에 없으면(stale ID) 예외 없이 drop하고
	 * 다음 항목을 탐색한다. 유효한 주문을 발견한 경우에만 {@code validateQueueOrderInvariant}가 호출된다.
	 * 유효 주문 반환 시 해당 가격 레벨이 비면 레벨 자체도 제거하고 {@code index}에서도 삭제한다.
	 *
	 * @param side {@link Side#BUY} → 최고 매수가, {@link Side#SELL} → 최저 매도가
	 * @return 최우선 주문. 호가 없으면 {@link Optional#empty()}
	 */
	public Optional<Order> poll(Side side) {
		return findBest(side, true);
	}

	/**
	 * 주문을 호가창에서 제거한다(취소용). {@code index}에서만 삭제하며 queue는 건드리지 않는다.
	 *
	 * <p>queue에 잔존하는 stale {@code OrderId}는 {@link #peek(Side)}/{@link #poll(Side)} 호출 시점에
	 * Lazy Removal로 조용히 제거된다.
	 *
	 * @param orderId 제거할 주문 ID
	 * @return 제거된 주문. 존재하지 않거나 이미 체결된 경우 {@link Optional#empty()}
	 */
	public Optional<Order> remove(OrderId orderId) {
		return Optional.ofNullable(index.remove(orderId));
	}

	// -------------------------------------------------------------------------
	// 내부 헬퍼
	// -------------------------------------------------------------------------

	/** side에 해당하는 호가창({@code bids} 또는 {@code asks})을 반환한다. */
	private NavigableMap<Price, Deque<OrderId>> bookOf(Side side) {
		return side == Side.BUY ? bids : asks;
	}

	/**
	 * 최우선 호가에서 유효한 주문을 찾는다. ghost(index에서 제거됐지만 큐에 남은) 엔트리를 정리하며 순회한다.
	 *
	 * @param side    조회할 사이드
	 * @param consume true면 큐에서 제거(poll), false면 유지(peek)
	 */
	private Optional<Order> findBest(Side side, boolean consume) {
		NavigableMap<Price, Deque<OrderId>> book = bookOf(side);

		while (true) {
			Map.Entry<Price, Deque<OrderId>> bestLevel = book.firstEntry();
			if (bestLevel == null) return Optional.empty();

			Deque<OrderId> queue = bestLevel.getValue();
			OrderId orderId = consume ? queue.pollFirst() : queue.peekFirst();
			if (orderId == null) {
				removeEmptyLevel(book, bestLevel);
				continue;
			}

			Order order = index.get(orderId);
			if (order == null) {
				if (!consume) queue.pollFirst();
				removeEmptyLevel(book, bestLevel);
				continue;
			}
			validateQueueOrderInvariant(order, side, bestLevel.getKey());

			if (consume) {
				removeEmptyLevel(bookOf(side), bestLevel);
				index.remove(orderId);
			}
			return Optional.of(order);
		}
	}

	/** 레벨 큐가 비어 있으면 해당 가격 레벨을 호가창에서 제거한다. */
	private void removeEmptyLevel(
		NavigableMap<Price, Deque<OrderId>> book,
		Map.Entry<Price, Deque<OrderId>> level
	) {
		if (level.getValue().isEmpty()) book.remove(level.getKey());
	}

	/**
	 * 호가창을 순회해 가격 레벨별 잔량 합계 스냅샷을 생성한다.
	 * 원본 comparator를 그대로 사용하므로 bids는 내림차순, asks는 오름차순으로 반환된다.
	 */
	private NavigableMap<Price, Quantity> aggregateDepth(NavigableMap<Price, Deque<OrderId>> book) {
		NavigableMap<Price, Quantity> snapshot = new TreeMap<>(book.comparator());
		book.forEach((price, queue) -> {
			long total = queue.stream()
				.map(index::get)
				.filter(Objects::nonNull)
				.mapToLong(order -> order.getRemaining().value())
				.sum();

			if (total > 0) snapshot.put(price, new Quantity(total));
		});
		return snapshot;
	}

	private void validateQueueOrderInvariant(Order order, Side side, Price price) {
		if (order.isMarket())
			throw new OrderBookInvariantViolationException("order is market: " + order.getOrderId());
		if (order.isFinal())
			throw new OrderBookInvariantViolationException("order is final: " + order.getOrderId());
		if (!order.getSide().equals(side))
			throw new OrderBookInvariantViolationException("side mismatch: " + order.getOrderId());
		if (!order.getLimitPriceOrThrow().equals(price))
			throw new OrderBookInvariantViolationException("price mismatch: " + order.getOrderId());
	}
}
