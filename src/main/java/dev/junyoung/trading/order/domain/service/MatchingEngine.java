package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.common.exception.ConflictException;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 단일 종목 주문 매칭 엔진. 가격-시간 우선(Price-Time Priority)으로 체결을 수행한다.
 * <p>{@link OrderBook} 상태 변경은 이 클래스 내부에서만 이루어지며, 체결 결과는 {@link PlaceResult}로 반환한다.</p>
 */
@RequiredArgsConstructor
public final class MatchingEngine {

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	private final OrderBook orderBook;

	// -------------------------------------------------------------------------
	// 진입점 (public API)
	// -------------------------------------------------------------------------

	/**
	 * 지정가 주문을 처리하고 반대 사이드 호가창과 매칭한다.
	 * <ol>
	 *   <li>주문 상태를 {@link OrderStatus#NEW}로 전환한다.</li>
	 *   <li>가격 조건을 만족하는 maker와 순서대로 체결한다 (가격 우선 → FIFO).</li>
	 *   <li>체결 후 잔량이 남으면 자신의 사이드 호가창에 등록한다.</li>
	 * </ol>
	 *
	 * @param taker 처리할 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	public PlaceResult placeLimitOrder(Order taker) {
		return placeOrder(taker, orderBook::add);
	}

	/**
	 * 지정가 IOC 주문을 처리한다. 가격 조건을 만족하는 반대 사이드 호가와 즉시 체결을 시도한다.
	 * <ol>
	 *   <li>주문 상태를 {@link OrderStatus#NEW}로 전환한다.</li>
	 *   <li>호가창이 빌 때까지 또는 잔량이 0이 될 때까지 체결한다.</li>
	 *   <li>체결 후 잔량이 남으면 {@link Order#cancel()}을 호출한다. 호가창에는 추가하지 않는다.</li>
	 * </ol>
	 *
	 * @param taker 처리할 지정가 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	public PlaceResult placeLimitOrderIOC(Order taker) {
		return placeOrder(taker, Order::cancel);
	}

	/**
	 * 지정가 FOK 주문을 처리한다. 전량 즉시 체결 가능한 경우에만 체결한다.
	 * <ol>
	 *   <li>반대 사이드에서 가격 조건을 만족하는 총 유동성을 집계한다.</li>
	 *   <li>유동성이 부족하면 {@link Order#activate()}와 {@link Order#cancel()}을 호출해
	 *       체결 없이 {@link OrderStatus#CANCELLED}로 전환한다. 호가창에는 추가하지 않는다.</li>
	 *   <li>유동성이 충분하면 일반 매칭 루프를 실행해 전량 체결한다 (결과는 항상 {@link OrderStatus#FILLED}).</li>
	 * </ol>
	 *
	 * @param taker 처리할 지정가 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	public PlaceResult placeLimitOrderFOK(Order taker) {
		Side makerSide = taker.getSide().opposite();
		Quantity availableQty = orderBook.totalAvailableQty(makerSide, taker.getLimitPriceOrThrow());

		if (availableQty.value() < taker.getQuantity().value()) {
			taker.activate();
			taker.cancel();
			return PlaceResult.of(List.of(taker), List.of());
		}

		return placeOrder(taker, Order::cancel);
	}

	/**
	 * MARKET SELL 주문을 처리한다. 가격 조건 없이 반대 사이드 최우선 호가부터 순차 체결한다.
	 * <ol>
	 *   <li>주문 상태를 {@link OrderStatus#NEW}로 전환한다.</li>
	 *   <li>호가창이 빌 때까지 또는 잔량이 0이 될 때까지 체결한다.</li>
	 *   <li>체결 후 잔량이 남으면 {@link Order#cancel()}을 호출한다. 호가창에는 추가하지 않는다.</li>
	 * </ol>
	 *
	 * @param taker 처리할 MARKET SELL 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	public PlaceResult placeMarketSellOrder(Order taker) {
		return placeOrder(taker, Order::cancel);
	}

	/**
	 * quoteQty(예산) 기반 MARKET BUY 주문을 처리한다.
	 * <ol>
	 *   <li>주문 상태를 {@link OrderStatus#NEW}로 전환한다.</li>
	 *   <li>예산이 소진되거나 호가창이 빌 때까지 SELL 호가와 체결한다.</li>
	 *   <li>1건 이상 체결됐으면 {@link Order#markFilledByMarketBuy()}로 FILLED 전이.</li>
	 *   <li>체결 0건이면 {@link Order#cancel()}로 CANCELLED 전이.</li>
	 * </ol>
	 *
	 * @param taker quoteQty 모드 MARKET BUY 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	public PlaceResult placeMarketBuyOrderWithQuoteQty(Order taker) {
		taker.activate();

		long remainingQuote = taker.getQuoteQty().value();
		int executedTradeCount = 0;
		List<Trade> trades = new ArrayList<>();
		List<Order> updatedMakers = new ArrayList<>();

		while (true) {
			Optional<Order> best = orderBook.peek(Side.SELL);
			if (best.isEmpty()) break;

			Order maker = best.get();
			Price executedPrice = maker.getLimitPriceOrThrow();
			long maxExecQty = remainingQuote / executedPrice.value();
			if (maxExecQty == 0) break;

			Quantity executedQty = new Quantity(Math.min(maxExecQty, maker.getRemaining().value()));
			trades.add(Trade.of(taker, maker, executedQty));
			long tradedQuote = Math.multiplyExact(executedPrice.value(), executedQty.value());

			maker.fill(executedQty, executedPrice);
			taker.fillQuoteMode(executedQty, executedPrice);
			remainingQuote = Math.subtractExact(remainingQuote, tradedQuote);
			executedTradeCount++;

			if (maker.getRemaining().value() == 0)
				orderBook.poll(Side.SELL);
			updatedMakers.add(maker);
		}

		if (executedTradeCount > 0)
			taker.markFilledByMarketBuy();
		else
			taker.cancel();

		List<Order> updatedOrders = Stream.concat(updatedMakers.stream(), Stream.of(taker)).toList();
		return PlaceResult.of(updatedOrders, trades);
	}

	/**
	 * 주문을 취소한다.
	 * <ol>
	 *   <li>호가창에서 해당 주문을 제거한다. 이미 체결되어 없는 경우 무시한다.</li>
	 *   <li>{@link Order#cancel()}을 호출해 상태를 {@link OrderStatus#CANCELLED}로 전환한다.</li>
	 * </ol>
	 *
	 * @param orderId 취소할 주문 ID
	 * @throws ConflictException 주문이 활성 상태({@link OrderStatus#NEW} /
	 *                           {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
	 */
	public Order cancelOrder(OrderId orderId) {
		Order order = orderBook.remove(orderId)
			.orElseThrow(() -> new ConflictException("ORDER_ALREADY_FINALIZED", "Already Processed or Cancelled Order"));

		order.cancel();
		return order;
	}

	// -------------------------------------------------------------------------
	// 내부 헬퍼
	// -------------------------------------------------------------------------

	/**
	 * 주문 처리의 공통 흐름을 실행하는 템플릿 메서드.
	 * <ol>
	 *   <li>taker를 활성 상태({@link OrderStatus#NEW})로 전환한다.</li>
	 *   <li>매칭 루프를 실행해 체결 가능한 maker와 순서대로 체결한다.</li>
	 *   <li>잔량이 남은 경우 {@code onRemaining} 전략에 따라 처리한다.
	 *       (예: 호가창 등록 또는 취소)</li>
	 *   <li>상태가 변경된 모든 주문(maker + taker)과 체결 내역을 {@link PlaceResult}로 반환한다.</li>
	 * </ol>
	 *
	 * @param taker       처리할 주문 ({@link OrderStatus#ACCEPTED} 상태)
	 * @param onRemaining 체결 후 잔량 처리 전략
	 *                    ({@code orderBook::add}: 호가창 등록 / {@code Order::cancel}: 즉시 취소)
	 * @return 상태 변경된 주문 목록과 체결 내역을 담은 {@link PlaceResult}
	 */
	private PlaceResult placeOrder(Order taker, Consumer<Order> onRemaining) {
		taker.activate();
		MatchLoopResult loop = runMatchingLoop(taker);
		if (taker.getRemaining().value() > 0)
			onRemaining.accept(taker);

		List<Order> updatedOrders = Stream.concat(loop.updatedMakers().stream(), Stream.of(taker)).toList();
		return PlaceResult.of(updatedOrders, loop.trades());
	}

	/**
	 * 반대 사이드 호가창을 순회하며 매칭 루프를 실행한다.
	 * 가격이 맞는 maker와 순서대로 체결하고, 완전 체결된 maker를 수집해 반환한다.
	 */
	private MatchLoopResult runMatchingLoop(Order taker) {
		List<Trade> trades = new ArrayList<>();
		List<Order> updatedMakers = new ArrayList<>();
		var side = taker.getSide().opposite();

		while (taker.getRemaining().value() > 0) {
			Optional<Order> best = orderBook.peek(side);
			if (best.isEmpty()) break;

			Order maker = best.get();
			if (!isPriceMatch(taker, maker)) break;

			Quantity executedQty = new Quantity(Math.min(taker.getRemaining().value(), maker.getRemaining().value()));
			Price executedPrice = maker.getLimitPriceOrThrow();
			trades.add(Trade.of(taker, maker, executedQty));

			maker.fill(executedQty, executedPrice);
			taker.fill(executedQty, executedPrice);
			if (maker.getRemaining().value() == 0)
				orderBook.poll(side);

			updatedMakers.add(maker);
		}
		return new MatchLoopResult(trades, updatedMakers);
	}

	/**
	 * taker와 maker 간 가격 매칭 여부를 판단한다.
	 * <ul>
	 *   <li>MARKET taker: 항상 {@code true} (가격 조건 없음)</li>
	 *   <li>BUY taker : maker 가격 ≤ taker 가격 (bestAsk ≤ buy price)</li>
	 *   <li>SELL taker: maker 가격 ≥ taker 가격 (bestBid ≥ sell price)</li>
	 * </ul>
	 */
	private boolean isPriceMatch(Order taker, Order maker) {
		if (taker.isMarket()) return true;
		long tp = taker.getLimitPriceOrThrow().value();
		long mp = maker.getLimitPriceOrThrow().value();
		return taker.getSide() == Side.BUY ? mp <= tp : mp >= tp;
	}

	// -------------------------------------------------------------------------
	// 내부 레코드
	// -------------------------------------------------------------------------

	private record MatchLoopResult(List<Trade> trades, List<Order> updatedMakers) {}
}
