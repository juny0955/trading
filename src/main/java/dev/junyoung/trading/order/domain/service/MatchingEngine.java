package dev.junyoung.trading.order.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.CancelResultCode;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.PlaceRejectCode;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.service.state.OrderBookView;

/**
 * 단일 종목 주문 매칭 엔진. 가격-시간 우선(Price-Time Priority)으로 체결을 수행한다.
 * <p>계산 전용 stateless 클래스. {@link OrderBookView}를 입력으로 받아 변경안을 계산하고,
 * 결과를 {@link PlaceCalculationResult}로 반환한다. live {@link OrderBookView}는 변경하지 않는다.</p>
 */
public final class MatchingEngine {

	// -------------------------------------------------------------------------
	// 진입점 (public API)
	// -------------------------------------------------------------------------

	public PlaceCalculationResult calculatePlace(OrderBookView view, Order taker) {
		if (taker.isMarket()) {
			if (taker.isBuy()) {
				if (!taker.isQuoteQtyMode())
					return new PlaceCalculationResult.Rejected(taker.getSymbol(), taker.getAcceptedSeq(), PlaceRejectCode.INVALID_QUANTITY_MODE);
				return calculateMarketBuy(view, taker);
			}
			return calculatePlaceOrder(view, taker, false);
		}

		return switch (taker.getTif()) {
			case GTC -> calculatePlaceOrder(view, taker, true);
			case IOC -> calculatePlaceOrder(view, taker, false);
			case FOK -> calculateFOK(view, taker);
		};
	}

	public CancelCalculationResult calculateCancel(OrderBookView view, Order target) {
		if (target.isFinal())
			return new CancelCalculationResult.Skipped(target.getSymbol(), target.getAcceptedSeq(), CancelResultCode.ORDER_ALREADY_FINAL);

		Order cancelled = target.cancel();
		view.removeInIndex(target.getOrderId());

		return new CancelCalculationResult.Cancelled(
			cancelled.getSymbol(),
			cancelled.getAcceptedSeq(),
			List.of(cancelled),
			List.of(new BookOperation.Remove(cancelled.getOrderId()))
		);
	}

	// -------------------------------------------------------------------------
	// 내부 헬퍼
	// -------------------------------------------------------------------------

	private PlaceCalculationResult calculatePlaceOrder(OrderBookView view, Order taker, boolean resting) {
		List<BookOperation> bookOps = new ArrayList<>();
		taker = taker.activate();

		MatchLoopResult loop = runMatchingLoop(view, taker, bookOps);
		taker = loop.updatedTaker();

		if (taker.getRemaining().value() > 0) {
			if (resting) {
				view.add(taker);
				bookOps.add(new BookOperation.Add(taker));
			} else {
				taker = taker.cancel();
			}
		}

		List<Order> updatedOrders = Stream.concat(Stream.of(taker), loop.updatedMakers().stream()).toList();
		return new PlaceCalculationResult.Accepted(taker.getSymbol(), taker.getAcceptedSeq(), updatedOrders, loop.trades(), bookOps);
	}

	private PlaceCalculationResult calculateFOK(OrderBookView view, Order taker) {
		Side makerSide = taker.getSide().opposite();
		Quantity available = view.totalAvailableQty(makerSide, taker.getLimitPriceOrThrow());

		if (available.value() < taker.getQuantity().value()) {
			taker = taker.activate();
			taker = taker.cancel();

			return new PlaceCalculationResult.Accepted(taker.getSymbol(), taker.getAcceptedSeq(), List.of(taker));
		}

		return calculatePlaceOrder(view, taker, false);
	}

	private PlaceCalculationResult calculateMarketBuy(OrderBookView view, Order taker) {
		List<BookOperation> bookOps = new ArrayList<>();
		List<Trade> trades = new ArrayList<>();
		List<Order> updatedMakers = new ArrayList<>();

		taker = taker.activate();

		long remainingQuote = taker.getQuoteQty().value();
		while (remainingQuote > 0) {
			Optional<Order> best = view.peek(Side.SELL);
			if (best.isEmpty()) break;

			Order maker = best.get();
			Price executedPrice = maker.getLimitPriceOrThrow();

			if (executedPrice.value() > remainingQuote) break;

			long maxExecQty = remainingQuote / executedPrice.value();
			long executedBase = Math.min(maxExecQty, maker.getRemaining().value());
			Quantity executedQty = new Quantity(executedBase);

			trades.add(Trade.of(taker, maker, executedQty));

			long tradedQuote = Math.multiplyExact(executedPrice.value(), executedQty.value());

			maker = maker.fill(executedQty, executedPrice);
			taker = taker.fillQuoteMode(executedQty, executedPrice);
			remainingQuote = Math.subtractExact(remainingQuote, tradedQuote);

			if (maker.getRemaining().value() == 0) {
				view.poll(Side.SELL);
				bookOps.add(new BookOperation.Remove(maker.getOrderId()));
			} else {
				view.replaceInIndex(maker);
				bookOps.add(new BookOperation.Replace(maker));
			}

			updatedMakers.add(maker);
		}

		taker = trades.isEmpty() ? taker.cancel() : taker.markFilledByMarketBuy();

		List<Order> updatedOrders = Stream.concat(Stream.of(taker), updatedMakers.stream()).toList();
		return new PlaceCalculationResult.Accepted(taker.getSymbol(), taker.getAcceptedSeq(), updatedOrders, trades, bookOps);
	}

	/**
	 * 반대 사이드 호가창을 순회하며 매칭 루프를 실행한다.
	 * 가격이 맞는 maker와 순서대로 체결하고, 완전 체결된 maker를 수집해 반환한다.
	 */
	private MatchLoopResult runMatchingLoop(OrderBookView view, Order taker, List<BookOperation> bookOps) {
		List<Trade> trades = new ArrayList<>();
		List<Order> updatedMakers = new ArrayList<>();
		Side side = taker.getSide().opposite();

		while (taker.getRemaining().value() > 0) {
			Optional<Order> best = view.peek(side);
			if (best.isEmpty()) break;

			Order maker = best.get();
			if (!isPriceMatch(taker, maker)) break;

			Quantity executedQty = new Quantity(Math.min(taker.getRemaining().value(), maker.getRemaining().value()));
			Price executedPrice = maker.getLimitPriceOrThrow();
			trades.add(Trade.of(taker, maker, executedQty));

			maker = maker.fill(executedQty, executedPrice);
			taker = taker.fill(executedQty, executedPrice);
			if (maker.getRemaining().value() == 0) {
				view.poll(side);
				bookOps.add(new BookOperation.Remove(maker.getOrderId()));
			} else {
				view.replaceInIndex(maker);
				bookOps.add(new BookOperation.Replace(maker));
			}
			updatedMakers.add(maker);
		}
		return new MatchLoopResult(trades, updatedMakers, taker);
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

	private record MatchLoopResult(List<Trade> trades, List<Order> updatedMakers, Order updatedTaker) {}
}
