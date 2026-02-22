package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

@DisplayName("MatchingEngine")
class MatchingEngineTest {

	private OrderBook orderBook;
	private MatchingEngine engine;

	@BeforeEach
	void setUp() {
		orderBook = new OrderBook();
		engine = new MatchingEngine(orderBook);
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	/** ACCEPTED 상태 BUY 주문 — 엔진에 직접 전달용 */
	private Order buyOrder(long price, long qty) {
		return new Order(Side.BUY, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED 상태 SELL 주문 — 엔진에 직접 전달용 */
	private Order sellOrder(long price, long qty) {
		return new Order(Side.SELL, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED → activate() → NEW 상태 SELL 주문 — orderBook 사전 등록용 */
	private Order activatedSellOrder(long price, long qty) {
		Order order = new Order(Side.SELL, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	/** ACCEPTED → activate() → NEW 상태 BUY 주문 — orderBook 사전 등록용 */
	private Order activatedBuyOrder(long price, long qty) {
		Order order = new Order(Side.BUY, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	// ── 매칭 없음 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("매칭 없음")
	class NoMatch {

		@Test
		@DisplayName("반대 사이드 주문이 없으면 Trade가 발생하지 않고 orderBook에 등록된다")
		void buyWithNoAsks_registeredToOrderBook() {
			Order buy = buyOrder(10_000, 5);

			List<Trade> trades = engine.placeLimitOrder(buy);

			assertThat(trades).isEmpty();
			assertThat(buy.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("SELL 진입 시 반대 BID가 없으면 Trade 없이 orderBook에 등록된다")
		void sellWithNoBids_registeredToOrderBook() {
			Order sell = sellOrder(10_000, 5);

			List<Trade> trades = engine.placeLimitOrder(sell);

			assertThat(trades).isEmpty();
			assertThat(sell.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("BUY 가격 < bestAsk 이면 가격 불일치로 체결되지 않고 orderBook에 등록된다")
		void buyPriceBelowBestAsk_noMatch() {
			orderBook.add(activatedSellOrder(11_000, 5));

			Order buy = buyOrder(10_000, 5);
			List<Trade> trades = engine.placeLimitOrder(buy);

			assertThat(trades).isEmpty();
			assertThat(buy.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestBid()).contains(new Price(10_000));
			assertThat(orderBook.bestAsk()).contains(new Price(11_000)); // ask 그대로
		}

		@Test
		@DisplayName("SELL 가격 > bestBid 이면 가격 불일치로 체결되지 않고 orderBook에 등록된다")
		void sellPriceAboveBestBid_noMatch() {
			orderBook.add(activatedBuyOrder(9_000, 5));

			Order sell = sellOrder(10_000, 5);
			List<Trade> trades = engine.placeLimitOrder(sell);

			assertThat(trades).isEmpty();
			assertThat(sell.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
			assertThat(orderBook.bestBid()).contains(new Price(9_000)); // bid 그대로
		}
	}

	// ── 완전 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("완전 체결")
	class FullMatch {

		@Test
		@DisplayName("taker qty == maker qty 이면 양쪽 모두 FILLED, Trade 1건 발생")
		void exactQtyMatch_bothFilled() {
			Order maker = activatedSellOrder(10_000, 5);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 5);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("완전 체결 후 orderBook은 비어있다")
		void exactQtyMatch_orderBookEmpty() {
			orderBook.add(activatedSellOrder(10_000, 5));

			engine.placeLimitOrder(buyOrder(10_000, 5));

			assertThat(orderBook.bestBid()).isEmpty();
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("Trade에 buyOrderId, sellOrderId, executedQty가 올바르게 기록된다")
		void tradeRecordsCorrectFields() {
			Order maker = activatedSellOrder(10_000, 5);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 5);
			Trade trade = engine.placeLimitOrder(taker).getFirst();

			assertThat(trade.buyOrderId()).isEqualTo(taker.getOrderId());
			assertThat(trade.sellOrderId()).isEqualTo(maker.getOrderId());
			assertThat(trade.executedQty()).isEqualTo(new Quantity(5));
		}

		@Test
		@DisplayName("SELL taker가 BUY maker와 완전 체결된다")
		void sellTakerFullyMatchesBuyMaker() {
			Order maker = activatedBuyOrder(10_000, 3);
			orderBook.add(maker);

			Order taker = sellOrder(10_000, 3);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
			Trade trade = trades.getFirst();
			assertThat(trade.buyOrderId()).isEqualTo(maker.getOrderId());
			assertThat(trade.sellOrderId()).isEqualTo(taker.getOrderId());
		}
	}

	// ── 부분 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("부분 체결")
	class PartialMatch {

		@Test
		@DisplayName("taker qty > maker qty: maker FILLED, taker PARTIALLY_FILLED 후 orderBook에 등록")
		void takerLargerThanMaker_takerRemainInBook() {
			Order maker = activatedSellOrder(10_000, 3);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 10);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(trades.getFirst().executedQty()).isEqualTo(new Quantity(3));
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(taker.getRemaining()).isEqualTo(new Quantity(7));
			assertThat(orderBook.bestBid()).contains(new Price(10_000)); // taker가 bid에 등록됨
			assertThat(orderBook.bestAsk()).isEmpty(); // maker 소진
		}

		@Test
		@DisplayName("taker qty < maker qty: taker FILLED, maker PARTIALLY_FILLED 상태로 orderBook에 잔류")
		void takerSmallerThanMaker_makerRemainsInBook() {
			Order maker = activatedSellOrder(10_000, 10);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 3);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(trades.getFirst().executedQty()).isEqualTo(new Quantity(3));
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(maker.getRemaining()).isEqualTo(new Quantity(7));
			assertThat(orderBook.bestAsk()).contains(new Price(10_000)); // maker 잔류
			assertThat(orderBook.bestBid()).isEmpty(); // taker 체결 완료
		}
	}

	// ── 연속 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("연속 체결")
	class MultiMatch {

		@Test
		@DisplayName("단일 BUY가 여러 ask를 순서대로 체결하면 Trade가 복수 발생한다")
		void singleBuySweepsMultipleAsks() {
			Order ask1 = activatedSellOrder(10_000, 3);
			Order ask2 = activatedSellOrder(10_000, 4);
			orderBook.add(ask1);
			orderBook.add(ask2);

			Order taker = buyOrder(10_000, 7);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(2);
			assertThat(trades.get(0).executedQty()).isEqualTo(new Quantity(3));
			assertThat(trades.get(1).executedQty()).isEqualTo(new Quantity(4));
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask1.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask2.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("BUY가 여러 가격 레벨을 순차 체결한다")
		void buySweeepsMultiplePriceLevels() {
			orderBook.add(activatedSellOrder(9_000, 2));
			orderBook.add(activatedSellOrder(10_000, 3));

			Order taker = buyOrder(10_000, 5);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(2);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("체결 수량이 부족해 일부 ask만 체결되면 나머지 ask는 orderBook에 잔류한다")
		void partialSweep_remainingAskStaysInBook() {
			Order ask1 = activatedSellOrder(10_000, 3);
			Order ask2 = activatedSellOrder(10_000, 5);
			orderBook.add(ask1);
			orderBook.add(ask2);

			Order taker = buyOrder(10_000, 3);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask1.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask2.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestAsk()).contains(new Price(10_000)); // ask2 잔류
		}
	}

	// ── 가격 우선 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("가격 우선")
	class PricePriority {

		@Test
		@DisplayName("BUY 진입 시 더 낮은 ask 가격이 먼저 체결된다")
		void buyMatchesLowestAskFirst() {
			Order highAsk = activatedSellOrder(11_000, 2);
			Order lowAsk  = activatedSellOrder(9_000, 2);
			orderBook.add(highAsk);
			orderBook.add(lowAsk);

			Order taker = buyOrder(11_000, 2);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(lowAsk.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(highAsk.getStatus()).isEqualTo(OrderStatus.NEW); // 미체결
		}

		@Test
		@DisplayName("SELL 진입 시 더 높은 bid 가격이 먼저 체결된다")
		void sellMatchesHighestBidFirst() {
			Order lowBid  = activatedBuyOrder(9_000, 2);
			Order highBid = activatedBuyOrder(11_000, 2);
			orderBook.add(lowBid);
			orderBook.add(highBid);

			Order taker = sellOrder(9_000, 2);
			List<Trade> trades = engine.placeLimitOrder(taker);

			assertThat(trades).hasSize(1);
			assertThat(highBid.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(lowBid.getStatus()).isEqualTo(OrderStatus.NEW); // 미체결
		}
	}

	// ── FIFO (시간 우선) ────────────────────────────────────────────────────

	@Nested
	@DisplayName("FIFO (시간 우선)")
	class FifoPriority {

		@Test
		@DisplayName("동일 ask 가격에서 먼저 등록된 주문이 먼저 체결된다")
		void samePriceAsks_earlierRegisteredMatchedFirst() {
			Order first  = activatedSellOrder(10_000, 3);
			Order second = activatedSellOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);

			Order taker = buyOrder(10_000, 3);
			engine.placeLimitOrder(taker);

			assertThat(first.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(second.getStatus()).isEqualTo(OrderStatus.NEW);
		}

		@Test
		@DisplayName("동일 bid 가격에서 먼저 등록된 주문이 먼저 체결된다")
		void samePriceBids_earlierRegisteredMatchedFirst() {
			Order first  = activatedBuyOrder(10_000, 3);
			Order second = activatedBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);


			Order taker = sellOrder(10_000, 3);
			engine.placeLimitOrder(taker);

			assertThat(first.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(second.getStatus()).isEqualTo(OrderStatus.NEW);
		}
	}

	// ── cancelOrder() ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("cancelOrder()")
	class CancelOrder {

		@Test
		@DisplayName("NEW 상태 BUY 주문이면 CANCELLED로 전이되고 orderBook에서 제거된다")
		void newBuyOrder_cancelledAndRemovedFromBook() {
			Order order = buyOrder(10_000, 5);
			engine.placeLimitOrder(order);

			engine.cancelOrder(order.getOrderId());

			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("NEW 상태 SELL 주문이면 CANCELLED로 전이되고 orderBook에서 제거된다")
		void newSellOrder_cancelledAndRemovedFromBook() {
			Order order = sellOrder(10_000, 5);
			engine.placeLimitOrder(order);

			engine.cancelOrder(order.getOrderId());

			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("PARTIALLY_FILLED 주문이면 CANCELLED로 전이되고 orderBook에서 제거된다")
		void partiallyFilledOrder_cancelledAndRemovedFromBook() {
			orderBook.add(activatedSellOrder(10_000, 3));

			Order order = buyOrder(10_000, 10); // qty=10, ask=3 → 7 잔량으로 PARTIALLY_FILLED 후 book 등록
			engine.placeLimitOrder(order);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);

			engine.cancelOrder(order.getOrderId());

			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("같은 가격 레벨에 주문이 2개이면 나머지 주문은 orderBook에 잔류한다")
		void oneOfTwoOrders_otherAtSameLevelRemains() {
			Order order1 = buyOrder(10_000, 3);
			Order order2 = buyOrder(10_000, 5);
			engine.placeLimitOrder(order1);
			engine.placeLimitOrder(order2);

			engine.cancelOrder(order1.getOrderId());

			assertThat(order1.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(orderBook.bestBid()).contains(new Price(10_000)); // order2 잔류
		}

		@Test
		@DisplayName("FILLED 주문이면 예외 없이 무시된다")
		void filledOrder_silentlyIgnored() {
			orderBook.add(activatedSellOrder(10_000, 5));
			Order order = buyOrder(10_000, 5);
			engine.placeLimitOrder(order);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);

			assertDoesNotThrow(() -> engine.cancelOrder(order.getOrderId()));
			assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("이미 CANCELLED된 주문이면 예외 없이 무시된다")
		void alreadyCancelledOrder_silentlyIgnored() {
			Order order = buyOrder(10_000, 5);
			engine.placeLimitOrder(order);
			engine.cancelOrder(order.getOrderId());
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

			assertDoesNotThrow(() -> engine.cancelOrder(order.getOrderId()));
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}
	}
}