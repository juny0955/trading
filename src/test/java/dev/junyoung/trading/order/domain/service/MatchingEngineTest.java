package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.order.fixture.OrderFixture;
import dev.junyoung.trading.common.exception.ConflictException;

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
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;

@DisplayName("MatchingEngine")
public class MatchingEngineTest {

	private OrderBook orderBook;
	private MatchingEngine engine;

	@BeforeEach
	void setUp() {
		orderBook = new OrderBook();
		engine = new MatchingEngine(orderBook);
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	private static final Symbol SYMBOL = new Symbol("BTC");

	/** ACCEPTED 상태 BUY 주문 — 엔진에 직접 전달용 */
	private Order buyOrder(long price, long qty) {
		return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED 상태 SELL 주문 — 엔진에 직접 전달용 */
	private Order sellOrder(long price, long qty) {
		return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED → activate() → NEW 상태 SELL 주문 — orderBook 사전 등록용 */
	private Order activatedSellOrder(long price, long qty) {
		Order order = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	/** ACCEPTED → activate() → NEW 상태 BUY 주문 — orderBook 사전 등록용 */
	private Order activatedBuyOrder(long price, long qty) {
		Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	private Order marketSellOrder(long qty) {
		return OrderFixture.createMarketSell(SYMBOL, new Quantity(qty));
	}

	// ── 매칭 없음 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("매칭 없음")
	class NoMatch {

		@Test
		@DisplayName("반대 사이드 주문이 없으면 Trade가 발생하지 않고 orderBook에 등록된다")
		void buyWithNoAsks_registeredToOrderBook() {
			Order buy = buyOrder(10_000, 5);

			List<Trade> trades = engine.placeLimitOrder(buy).trades();

			assertThat(trades).isEmpty();
			assertThat(buy.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("SELL 진입 시 반대 BID가 없으면 Trade 없이 orderBook에 등록된다")
		void sellWithNoBids_registeredToOrderBook() {
			Order sell = sellOrder(10_000, 5);

			List<Trade> trades = engine.placeLimitOrder(sell).trades();

			assertThat(trades).isEmpty();
			assertThat(sell.getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("BUY 가격 < bestAsk 이면 가격 불일치로 체결되지 않고 orderBook에 등록된다")
		void buyPriceBelowBestAsk_noMatch() {
			orderBook.add(activatedSellOrder(11_000, 5));

			Order buy = buyOrder(10_000, 5);
			List<Trade> trades = engine.placeLimitOrder(buy).trades();

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
			List<Trade> trades = engine.placeLimitOrder(sell).trades();

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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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
			Trade trade = engine.placeLimitOrder(taker).trades().getFirst();

			assertThat(trade.buyOrderId()).isEqualTo(taker.getOrderId());
			assertThat(trade.sellOrderId()).isEqualTo(maker.getOrderId());
			assertThat(trade.quantity()).isEqualTo(new Quantity(5));
		}

		@Test
		@DisplayName("SELL taker가 BUY maker와 완전 체결된다")
		void sellTakerFullyMatchesBuyMaker() {
			Order maker = activatedBuyOrder(10_000, 3);
			orderBook.add(maker);

			Order taker = sellOrder(10_000, 3);
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

			assertThat(trades).hasSize(1);
			assertThat(trades.getFirst().quantity()).isEqualTo(new Quantity(3));
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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

			assertThat(trades).hasSize(1);
			assertThat(trades.getFirst().quantity()).isEqualTo(new Quantity(3));
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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

			assertThat(trades).hasSize(2);
			assertThat(trades.get(0).quantity()).isEqualTo(new Quantity(3));
			assertThat(trades.get(1).quantity()).isEqualTo(new Quantity(4));
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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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
			List<Trade> trades = engine.placeLimitOrder(taker).trades();

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

	// ── placeMarketSellOrder() ────────────────────────────────────────────

	@Nested
	@DisplayName("placeMarketSellOrder()")
	class PlaceMarketSellOrder {

		@Test
		@DisplayName("SELL MARKET — 호가창이 비어있으면 체결 없이 즉시 CANCELLED")
		void sellMarket_emptyBook_immediatelyCancelled() {
			Order taker = marketSellOrder(5);
			engine.placeMarketSellOrder(taker);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("SELL MARKET — 전량 체결 → FILLED, remaining = 0")
		void sellMarket_exactMatch_filled() {
			orderBook.add(activatedBuyOrder(10_000, 5));
			Order taker = marketSellOrder(5);
			List<Trade> trades = engine.placeMarketSellOrder(taker).trades();
			assertThat(trades).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(taker.getRemaining()).isEqualTo(new Quantity(0));
		}

		@Test
		@DisplayName("SELL MARKET — 유동성 부족 → 부분 체결 후 잔량 CANCELLED")
		void sellMarket_insufficientLiquidity_partialFillThenCancelled() {
			orderBook.add(activatedBuyOrder(10_000, 3));
			Order taker = marketSellOrder(10);
			List<Trade> trades = engine.placeMarketSellOrder(taker).trades();
			assertThat(trades).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(taker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("MARKET 주문은 잔량이 남아도 orderBook에 등록되지 않는다")
		void marketOrder_remainingNotAddedToOrderBook() {
			Order taker = marketSellOrder(5);
			engine.placeMarketSellOrder(taker);
			assertThat(orderBook.bestBid()).isEmpty();
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("PlaceResult의 updatedOrders에 taker가 항상 포함된다")
		void placeMarketSellOrder_updatedOrdersAlwaysContainsTaker() {
			Order taker = marketSellOrder(5);
			PlaceResult result = engine.placeMarketSellOrder(taker);
			assertThat(result.updatedOrders()).contains(taker);
		}
	}

	// ── PlaceResult.updatedOrders 영속화 계약 ──────────────────────────────

	/**
	 * 부분 체결된 maker가 updatedOrders에서 누락되는 버그를 검증한다.
	 *
 * <p>이전 구현에서는 완전 체결(FILLED)된 maker만 updatedOrders에 포함시키는 버그가 있었습니다.
 * 부분 체결(PARTIALLY_FILLED)된 maker는 메모리 상태가 변경됐음에도 누락되어
 * 영속 DB 전환 시 이중 체결(double execution)을 유발할 수 있다.</p>
	 */
	@Nested
	@DisplayName("PlaceResult.updatedOrders 영속화 계약")
	class UpdatedOrdersPersistenceContract {

		@Test
		@DisplayName("LIMIT taker가 maker를 부분 체결하면 부분 체결된 maker가 updatedOrders에 포함되어야 한다")
		void limitOrder_partiallyFilledMaker_mustBeInUpdatedOrders() {
			Order maker = activatedSellOrder(10_000, 10);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 3); // taker qty(3) < maker qty(10) → maker PARTIALLY_FILLED
			PlaceResult result = engine.placeLimitOrder(taker);

			assertThat(maker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(result.updatedOrders()).contains(maker);
		}

		@Test
		@DisplayName("완전 체결된 maker는 updatedOrders에 포함된다 (기존 동작 보호)")
		void limitOrder_fullyFilledMaker_isInUpdatedOrders() {
			Order maker = activatedSellOrder(10_000, 5);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrder(taker);

			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(result.updatedOrders()).contains(maker);
		}

		@Test
		@DisplayName("체결에 참여한 taker와 부분 체결된 maker 모두 updatedOrders에 포함되어야 한다")
		void limitOrder_allAffectedOrders_includedInUpdatedOrders() {
			Order maker = activatedSellOrder(10_000, 10);
			orderBook.add(maker);

			Order taker = buyOrder(10_000, 3);
			PlaceResult result = engine.placeLimitOrder(taker);

			assertThat(result.updatedOrders()).containsExactlyInAnyOrder(taker, maker);
		}
	}

	// ── placeLimitOrderIOC() ─────────────────────────────────────────────

	@Nested
	@DisplayName("placeLimitOrderIOC()")
	class PlaceLimitOrderIOC {

		private Order iocBuyOrder(long price, long qty) {
			return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.IOC, new Price(price), new Quantity(qty));
		}

		private Order iocSellOrder(long price, long qty) {
			return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.IOC, new Price(price), new Quantity(qty));
		}

		@Test
		@DisplayName("반대 호가 없음 → 체결 없이 즉시 CANCELLED")
		void noOppositeOrder_immediatelyCancelled() {
			Order taker = iocBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("가격 불일치 → 체결 없이 즉시 CANCELLED")
		void priceMismatch_immediatelyCancelled() {
			orderBook.add(activatedSellOrder(11_000, 5));
			Order taker = iocBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("전량 체결 → taker FILLED, Trade 1건 발생")
		void exactMatch_takerFilled() {
			orderBook.add(activatedSellOrder(10_000, 5));
			Order taker = iocBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("부분 체결 — taker qty > maker qty → maker FILLED, taker 잔량 CANCELLED")
		void partialMatch_takerLarger_makerFilledTakerCancelled() {
			Order maker = activatedSellOrder(10_000, 3);
			orderBook.add(maker);
			Order taker = iocBuyOrder(10_000, 10);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(taker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("부분 체결 — taker qty < maker qty → taker FILLED, maker PARTIALLY_FILLED")
		void partialMatch_takerSmaller_takerFilledMakerPartiallyFilled() {
			Order maker = activatedSellOrder(10_000, 10);
			orderBook.add(maker);
			Order taker = iocBuyOrder(10_000, 3);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(maker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("여러 maker와 연속 체결 후 잔량 → taker CANCELLED, trades N건")
		void multipleMatchesThenRemaining_takerCancelled() {
			orderBook.add(activatedSellOrder(10_000, 2));
			orderBook.add(activatedSellOrder(10_000, 3));
			Order taker = iocBuyOrder(10_000, 10);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).hasSize(2);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(taker.getRemaining()).isEqualTo(new Quantity(5));
		}

		@Test
		@DisplayName("IOC taker는 잔량이 있어도 orderBook에 등록되지 않는다")
		void iocTaker_remainingNotAddedToOrderBook() {
			Order taker = iocBuyOrder(10_000, 5);
			engine.placeLimitOrderIOC(taker);
			assertThat(orderBook.bestBid()).isEmpty();
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("부분 체결 후 잔량이 남아도 IOC taker가 orderBook에 없다")
		void partialMatch_iocTakerNotInOrderBook() {
			orderBook.add(activatedSellOrder(10_000, 3));
			Order taker = iocBuyOrder(10_000, 10);
			engine.placeLimitOrderIOC(taker);
			assertThat(orderBook.bestBid()).isEmpty();
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("IOC SELL — 반대 호가 없음 → 즉시 CANCELLED")
		void iocSell_noOppositeOrder_immediatelyCancelled() {
			Order taker = iocSellOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("PlaceResult의 updatedOrders에 IOC taker가 항상 포함된다")
		void placeLimitOrderIOC_updatedOrdersAlwaysContainsTaker() {
			Order taker = iocBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderIOC(taker);
			assertThat(result.updatedOrders()).contains(taker);
		}
	}

	// ── placeLimitOrderFOK() ─────────────────────────────────────────────

	@Nested
	@DisplayName("placeLimitOrderFOK()")
	class PlaceLimitOrderFOK {

		private Order fokBuyOrder(long price, long qty) {
			return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.FOK, new Price(price), new Quantity(qty));
		}

		private Order fokSellOrder(long price, long qty) {
			return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.FOK, new Price(price), new Quantity(qty));
		}

		@Test
		@DisplayName("반대 호가 없음 → 체결 없이 즉시 CANCELLED")
		void noOppositeOrder_immediatelyCancelled() {
			Order taker = fokBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("가격 불일치 → 체결 없이 즉시 CANCELLED")
		void priceMismatch_immediatelyCancelled() {
			orderBook.add(activatedSellOrder(11_000, 5));
			Order taker = fokBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("유동성 부족 (일부만 충족) → 체결 없이 즉시 CANCELLED")
		void insufficientLiquidity_immediatelyCancelled() {
			orderBook.add(activatedSellOrder(10_000, 3));
			Order taker = fokBuyOrder(10_000, 10);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("단일 maker 전량 체결 → Trade 1건, taker FILLED")
		void exactMatch_singleMaker_filled() {
			Order maker = activatedSellOrder(10_000, 5);
			orderBook.add(maker);
			Order taker = fokBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("여러 maker와 전량 체결 → Trade N건, taker FILLED")
		void multiMaker_fullFill_filled() {
			orderBook.add(activatedSellOrder(10_000, 3));
			orderBook.add(activatedSellOrder(10_000, 4));
			Order taker = fokBuyOrder(10_000, 7);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).hasSize(2);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("taker qty < maker qty → taker FILLED, maker PARTIALLY_FILLED")
		void takerSmallerThanMaker_takerFilledMakerPartiallyFilled() {
			Order maker = activatedSellOrder(10_000, 10);
			orderBook.add(maker);
			Order taker = fokBuyOrder(10_000, 3);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.trades()).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(maker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(maker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("FOK taker는 체결 여부와 무관하게 orderBook에 등록되지 않는다")
		void fokTaker_neverAddedToOrderBook() {
			Order taker = fokBuyOrder(10_000, 5);
			engine.placeLimitOrderFOK(taker);
			assertThat(orderBook.bestBid()).isEmpty();
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("PlaceResult의 updatedOrders에 FOK taker가 항상 포함된다")
		void placeLimitOrderFOK_updatedOrdersAlwaysContainsTaker() {
			Order taker = fokBuyOrder(10_000, 5);
			PlaceResult result = engine.placeLimitOrderFOK(taker);
			assertThat(result.updatedOrders()).contains(taker);
		}
	}

	// ── placeMarketBuyOrderWithQuoteQty() ─────────────────────────────────

	@Nested
	@DisplayName("placeMarketBuyOrderWithQuoteQty()")
	class PlaceMarketBuyOrderWithQuoteQty {

		private Order quoteQtyBuyOrder(long quoteQtyValue) {
			return OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(quoteQtyValue));
		}

		@Test
		@DisplayName("ask 없음 → 체결 없이 CANCELLED, trades 0건")
		void noAsk_thenCancelled() {
			Order taker = quoteQtyBuyOrder(50_000L);
			PlaceResult result = engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("ask 1건, 예산 완전 소진 → FILLED, trades 1건")
		void singleAsk_fullyConsumed_thenFilled() {
			orderBook.add(activatedSellOrder(10_000, 5)); // 10_000 * 5 = 50_000
			Order taker = quoteQtyBuyOrder(50_000L);
			PlaceResult result = engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(result.trades()).hasSize(1);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("ask 여러 건, 예산 소진 → FILLED, trades > 1건")
		void multipleAsks_budgetExhausted_thenFilled() {
			Order ask1 = activatedSellOrder(10_000, 2);
			Order ask2 = activatedSellOrder(10_000, 2);
			orderBook.add(ask1);
			orderBook.add(ask2);
			Order taker = quoteQtyBuyOrder(40_000L); // 10_000 * (2 + 2) = 40_000
			PlaceResult result = engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(result.trades()).hasSize(2);
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask1.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask2.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("예산 < ask 최저가 → 체결 없이 CANCELLED, trades 0건")
		void budgetLessThanMinAskPrice_thenCancelled() {
			orderBook.add(activatedSellOrder(10_000, 5));
			Order taker = quoteQtyBuyOrder(9_999L); // 예산 9_999 < 최저가 10_000

			PlaceResult result = engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(result.trades()).isEmpty();
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("체결 후 예산 잔여 → FILLED (잔여 예산 무관)")
		void partialBudgetRemaining_thenFilled() {
			Order ask = activatedSellOrder(10_000, 3);
			orderBook.add(ask); // 3건 체결 = 30_000 소진
			Order taker = quoteQtyBuyOrder(35_000L); // 5_000 잔여

			PlaceResult result = engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(taker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(ask.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("단일 ask 체결 후 cumQuoteQty/cumBaseQty가 누적된다")
		void singleAsk_accumulates_cumQuoteQtyAndCumBaseQty() {
			orderBook.add(activatedSellOrder(10_000, 5)); // 10_000 * 5 = 50_000
			Order taker = quoteQtyBuyOrder(50_000L);

			engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(taker.getCumQuoteQty().value()).isEqualTo(50_000L);
			assertThat(taker.getCumBaseQty().value()).isEqualTo(5L);
		}

		@Test
		@DisplayName("복수 ask 체결 후 cumQuoteQty/cumBaseQty가 루프마다 누적된다")
		void multipleAsks_accumulates_acrossAllTrades() {
			orderBook.add(activatedSellOrder(10_000, 2)); // 20_000
			orderBook.add(activatedSellOrder(10_000, 2)); // 20_000
			Order taker = quoteQtyBuyOrder(40_000L);

			engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(taker.getCumQuoteQty().value()).isEqualTo(40_000L);
			assertThat(taker.getCumBaseQty().value()).isEqualTo(4L);
		}

		@Test
		@DisplayName("체결 없으면 cumQuoteQty/cumBaseQty는 0")
		void noFill_cumFieldsAreZero() {
			Order taker = quoteQtyBuyOrder(50_000L);

			engine.placeMarketBuyOrderWithQuoteQty(taker);

			assertThat(taker.getCumQuoteQty().value()).isEqualTo(0L);
			assertThat(taker.getCumBaseQty().value()).isEqualTo(0L);
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
		@DisplayName("FILLED 주문 취소 시 ConflictException이 발생한다")
		void filledOrder_throwsConflictException() {
			orderBook.add(activatedSellOrder(10_000, 5));
			Order order = buyOrder(10_000, 5);
			engine.placeLimitOrder(order);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);

			assertThrows(ConflictException.class, () -> engine.cancelOrder(order.getOrderId()));
			assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("이미 CANCELLED된 주문 취소 시 ConflictException이 발생한다")
		void alreadyCancelledOrder_throwsConflictException() {
			Order order = buyOrder(10_000, 5);
			engine.placeLimitOrder(order);
			engine.cancelOrder(order.getOrderId());
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

			assertThrows(ConflictException.class, () -> engine.cancelOrder(order.getOrderId()));
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}
	}
}
