package dev.junyoung.trading.order.application.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import lombok.RequiredArgsConstructor;

/**
 * 단일 종목 지정가(LIMIT) 주문 매칭 엔진. 가격-시간 우선(Price-Time Priority)으로 체결을 수행한다.
 * <p>{@link OrderBook} 상태 변경은 이 클래스 내부에서만 이루어지며, 체결 결과는 {@link Trade} 목록으로 반환한다.</p>
 */
@Component
@RequiredArgsConstructor
public class MatchingEngine {

	private final OrderBook orderBook;

	/**
	 * 지정가 주문을 처리하고 반대 사이드 호가창과 매칭한다.
	 * <ol>
	 *   <li>주문 상태를 {@link OrderStatus#NEW}로 전환한다.</li>
	 *   <li>가격 조건을 만족하는 maker와 순서대로 체결한다 (가격 우선 → FIFO).</li>
	 *   <li>체결 후 잔량이 남으면 자신의 사이드 호가창에 등록한다.</li>
	 * </ol>
	 *
	 * @param taker 처리할 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 이번 주문으로 발생한 {@link Trade} 목록. 체결 없으면 빈 리스트.
	 */
	public List<Trade> placeLimitOrder(Order taker) {
		taker.activate();

		List<Trade> trades = new ArrayList<>();
		Side side = taker.getSide().opposite();
		while (taker.getRemaining().value() > 0) {
			Optional<Order> best = orderBook.peek(side);
			if (best.isEmpty()) break;

			Order maker = best.get();
			if (!isPriceMatch(taker, maker)) break;

			Quantity qty = new Quantity(Math.min(taker.getRemaining().value(), maker.getRemaining().value()));
			trades.add(Trade.of(taker, maker, qty));

			maker.fill(qty);
			taker.fill(qty);
			if (maker.getRemaining().value() == 0) orderBook.poll(side);
		}

		if (taker.getRemaining().value() > 0) {
			orderBook.add(taker);
		}

		return trades;
	}

	/**
	 * 주문을 취소한다.
	 * <ol>
	 *   <li>호가창에서 해당 주문을 제거한다. 이미 체결되어 없는 경우 무시한다.</li>
	 *   <li>{@link Order#cancel()}을 호출해 상태를 {@link OrderStatus#CANCELLED}로 전환한다.</li>
	 * </ol>
	 *
	 * @param orderId 취소할 주문 ID
	 * @throws IllegalStateException 주문이 활성 상태({@link OrderStatus#NEW} /
	 *                               {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
	 */
	public Order cancelOrder(OrderId orderId) {
		Order order = orderBook.remove(orderId)
			.orElseThrow(() -> new IllegalStateException("Already Processed or Cancelled Order"));

		order.cancel();
		return order;
	}

	/**
	 * taker와 maker 간 가격 매칭 여부를 판단한다.
	 * <ul>
	 *   <li>BUY taker : maker 가격 ≤ taker 가격 (bestAsk ≤ buy price)</li>
	 *   <li>SELL taker: maker 가격 ≥ taker 가격 (bestBid ≥ sell price)</li>
	 * </ul>
	 */
	private boolean isPriceMatch(Order taker, Order maker) {
		long tp = taker.getPrice().value();
		long mp = maker.getPrice().value();
		return taker.getSide() == Side.BUY ? mp <= tp : mp >= tp;
	}
}
