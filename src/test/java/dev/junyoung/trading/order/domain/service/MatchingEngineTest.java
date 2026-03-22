package dev.junyoung.trading.order.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.order.application.engine.OrderBookViewFactory;
import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.application.engine.dto.CancelCalculationResult;
import dev.junyoung.trading.order.application.engine.dto.CancelResultCode;
import dev.junyoung.trading.order.application.engine.dto.PlaceCalculationResult;
import dev.junyoung.trading.order.domain.service.dto.CancelCalculationInput;
import dev.junyoung.trading.order.domain.service.dto.PlaceCalculationInput;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.state.OrderBookView;
import dev.junyoung.trading.order.fixture.OrderFixture;

@DisplayName("MatchingEngine")
public class MatchingEngineTest {

	private static final Symbol SYMBOL = new Symbol("BTC");

	private OrderBook orderBook;
	private MatchingEngine engine;

	@BeforeEach
	void setUp() {
		orderBook = new OrderBook();
		engine = new MatchingEngine();
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	/** 현재 orderBook 상태로 OrderBookView 스냅샷을 생성한다. 매칭 직전에 호출한다. */
	private OrderBookView view() {
		return OrderBookViewFactory.create(orderBook);
	}

	/** ACCEPTED 상태 GTC LIMIT BUY taker */
	private Order takerBuy(long price, long qty) {
		return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED 상태 GTC LIMIT SELL taker */
	private Order takerSell(long price, long qty) {
		return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
	}

	/** ACCEPTED 상태 MARKET SELL taker */
	private Order takerMarketSell(long qty) {
		return OrderFixture.createMarketSell(SYMBOL, new Quantity(qty));
	}

	/** ACCEPTED 상태 quoteQty 기반 MARKET BUY taker */
	private Order takerMarketBuy(long quoteQtyValue) {
		return OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(quoteQtyValue));
	}

	/** ACTIVATED LIMIT SELL maker를 orderBook에 등록하고 반환한다 */
	private Order addMakerSell(long price, long qty) {
		Order activated = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
		orderBook.add(activated);
		return activated;
	}

	/** ACTIVATED LIMIT BUY maker를 orderBook에 등록하고 반환한다 */
	private Order addMakerBuy(long price, long qty) {
		Order activated = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
		orderBook.add(activated);
		return activated;
	}

	private PlaceCalculationResult place(Order order) {
		return engine.calculatePlace(new PlaceCalculationInput(view(), order));
	}

	private CancelCalculationResult cancel(Order target) {
		return engine.calculateCancel(new CancelCalculationInput(
			view(),
			target.getSymbol(),
			target.getOrderId(),
			target.getAccountId(),
			target
		));
	}

	/** PlaceCalculationResult가 Accepted임을 단언하며 추출한다 */
	private static PlaceCalculationResult.Accepted accepted(PlaceCalculationResult result) {
		return assertInstanceOf(PlaceCalculationResult.Accepted.class, result);
	}

	/** updatedOrders에서 orderId로 최종 주문 상태를 찾는다 */
	private static Order findOrder(PlaceCalculationResult.Accepted result, OrderId id) {
		return result.updatedOrders().stream()
			.filter(o -> o.getOrderId().equals(id))
			.findFirst()
			.orElseThrow(() -> new AssertionError("orderId " + id + " not found in updatedOrders"));
	}

	// ── 매칭 없음 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("매칭 없음")
	class NoMatch {

		@Test
		@DisplayName("반대 사이드 주문이 없으면 Trade가 발생하지 않고 bookOps에 Add가 포함된다")
		void buyWithNoAsks_registeredAsAdd() {
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Add a && a.order().getOrderId().equals(taker.getOrderId()));
		}

		@Test
		@DisplayName("SELL 진입 시 반대 BID가 없으면 Trade 없이 bookOps에 Add가 포함된다")
		void sellWithNoBids_registeredAsAdd() {
			Order taker = takerSell(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Add a && a.order().getOrderId().equals(taker.getOrderId()));
		}

		@Test
		@DisplayName("BUY 가격 < bestAsk 이면 가격 불일치로 체결되지 않고 bookOps에 Add가 포함된다")
		void buyPriceBelowBestAsk_noMatch() {
			addMakerSell(11_000, 5);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Add a && a.order().getOrderId().equals(taker.getOrderId()));
		}

		@Test
		@DisplayName("SELL 가격 > bestBid 이면 가격 불일치로 체결되지 않고 bookOps에 Add가 포함된다")
		void sellPriceAboveBestBid_noMatch() {
			addMakerBuy(9_000, 5);
			Order taker = takerSell(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.NEW);
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Add a && a.order().getOrderId().equals(taker.getOrderId()));
		}
	}

	// ── 완전 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("완전 체결")
	class FullMatch {

		@Test
		@DisplayName("taker qty == maker qty 이면 양쪽 모두 FILLED, Trade 1건 발생")
		void exactQtyMatch_bothFilled() {
			Order maker = addMakerSell(10_000, 5);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("완전 체결 후 bookOps에 taker Add 없음, maker Remove 있음")
		void exactQtyMatch_bookOpsHasRemoveForMaker() {
			Order maker = addMakerSell(10_000, 5);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.bookOps()).noneMatch(op -> op instanceof BookOperation.Add);
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Remove r && r.orderId().equals(maker.getOrderId()));
		}

		@Test
		@DisplayName("Trade에 buyOrderId, sellOrderId, executedQty가 올바르게 기록된다")
		void tradeRecordsCorrectFields() {
			Order maker = addMakerSell(10_000, 5);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			var trade = result.trades().getFirst();
			assertThat(trade.buyOrderId()).isEqualTo(taker.getOrderId());
			assertThat(trade.sellOrderId()).isEqualTo(maker.getOrderId());
			assertThat(trade.quantity()).isEqualTo(new Quantity(5));
		}

		@Test
		@DisplayName("SELL taker가 BUY maker와 완전 체결된다")
		void sellTakerFullyMatchesBuyMaker() {
			Order maker = addMakerBuy(10_000, 3);
			Order taker = takerSell(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			var trade = result.trades().getFirst();
			assertThat(trade.buyOrderId()).isEqualTo(maker.getOrderId());
			assertThat(trade.sellOrderId()).isEqualTo(taker.getOrderId());
		}
	}

	// ── 부분 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("부분 체결")
	class PartialMatch {

		@Test
		@DisplayName("taker qty > maker qty: maker FILLED, taker PARTIALLY_FILLED 후 bookOps에 Add")
		void takerLargerThanMaker_takerAddsToBook() {
			Order maker = addMakerSell(10_000, 3);
			Order taker = takerBuy(10_000, 10);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(finalTaker.getRemaining()).isEqualTo(new Quantity(7));
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Add a && a.order().getOrderId().equals(taker.getOrderId()));
		}

		@Test
		@DisplayName("taker qty < maker qty: taker FILLED, maker PARTIALLY_FILLED, bookOps에 Replace")
		void takerSmallerThanMaker_makerReplaced() {
			Order maker = addMakerSell(10_000, 10);
			Order taker = takerBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			Order finalMaker = findOrder(result, maker.getOrderId());
			assertThat(finalMaker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(finalMaker.getRemaining()).isEqualTo(new Quantity(7));
			assertThat(result.bookOps()).anyMatch(op ->
				op instanceof BookOperation.Replace r && r.updatedOrder().getOrderId().equals(maker.getOrderId()));
		}
	}

	// ── 연속 체결 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("연속 체결")
	class MultiMatch {

		@Test
		@DisplayName("단일 BUY가 여러 ask를 순서대로 체결하면 Trade가 복수 발생한다")
		void singleBuySweepsMultipleAsks() {
			Order ask1 = addMakerSell(10_000, 3);
			Order ask2 = addMakerSell(10_000, 4);
			Order taker = takerBuy(10_000, 7);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(2);
			assertThat(result.trades().get(0).quantity()).isEqualTo(new Quantity(3));
			assertThat(result.trades().get(1).quantity()).isEqualTo(new Quantity(4));
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask1.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask2.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("BUY가 여러 가격 레벨을 순차 체결한다")
		void buySweeepsMultiplePriceLevels() {
			addMakerSell(9_000, 2);
			addMakerSell(10_000, 3);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(2);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("체결 수량이 부족해 일부 ask만 체결되면 나머지 ask는 updatedOrders에 포함되지 않는다")
		void partialSweep_untouchedAskNotInUpdatedOrders() {
			Order ask1 = addMakerSell(10_000, 3);
			Order ask2 = addMakerSell(10_000, 5);
			Order taker = takerBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask1.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(result.updatedOrders().stream()
				.noneMatch(o -> o.getOrderId().equals(ask2.getOrderId()))).isTrue();
		}
	}

	// ── 가격 우선 ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("가격 우선")
	class PricePriority {

		@Test
		@DisplayName("BUY 진입 시 더 낮은 ask 가격이 먼저 체결된다")
		void buyMatchesLowestAskFirst() {
			Order highAsk = addMakerSell(11_000, 2);
			Order lowAsk  = addMakerSell(9_000, 2);
			Order taker = takerBuy(11_000, 2);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().sellOrderId()).isEqualTo(lowAsk.getOrderId());
			assertThat(result.updatedOrders().stream()
				.noneMatch(o -> o.getOrderId().equals(highAsk.getOrderId()))).isTrue();
		}

		@Test
		@DisplayName("SELL 진입 시 더 높은 bid 가격이 먼저 체결된다")
		void sellMatchesHighestBidFirst() {
			Order lowBid  = addMakerBuy(9_000, 2);
			Order highBid = addMakerBuy(11_000, 2);
			Order taker = takerSell(9_000, 2);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().buyOrderId()).isEqualTo(highBid.getOrderId());
			assertThat(result.updatedOrders().stream()
				.noneMatch(o -> o.getOrderId().equals(lowBid.getOrderId()))).isTrue();
		}
	}

	// ── FIFO (시간 우선) ────────────────────────────────────────────────────

	@Nested
	@DisplayName("FIFO (시간 우선)")
	class FifoPriority {

		@Test
		@DisplayName("동일 ask 가격에서 먼저 등록된 주문이 먼저 체결된다")
		void samePriceAsks_earlierRegisteredMatchedFirst() {
			Order first  = addMakerSell(10_000, 3);
			Order second = addMakerSell(10_000, 3);
			Order taker = takerBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().sellOrderId()).isEqualTo(first.getOrderId());
			assertThat(result.updatedOrders().stream()
				.noneMatch(o -> o.getOrderId().equals(second.getOrderId()))).isTrue();
		}

		@Test
		@DisplayName("동일 bid 가격에서 먼저 등록된 주문이 먼저 체결된다")
		void samePriceBids_earlierRegisteredMatchedFirst() {
			Order first  = addMakerBuy(10_000, 3);
			Order second = addMakerBuy(10_000, 3);
			Order taker = takerSell(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().buyOrderId()).isEqualTo(first.getOrderId());
			assertThat(result.updatedOrders().stream()
				.noneMatch(o -> o.getOrderId().equals(second.getOrderId()))).isTrue();
		}
	}

	// ── MARKET SELL ────────────────────────────────────────────────────────

	@Nested
	@DisplayName("MARKET SELL")
	class PlaceMarketSellOrder {

		@Test
		@DisplayName("호가창이 비어있으면 체결 없이 즉시 CANCELLED")
		void sellMarket_emptyBook_immediatelyCancelled() {
			Order taker = takerMarketSell(5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("전량 체결 → FILLED, remaining = 0")
		void sellMarket_exactMatch_filled() {
			addMakerBuy(10_000, 5);
			Order taker = takerMarketSell(5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(finalTaker.getRemaining()).isEqualTo(new Quantity(0));
		}

		@Test
		@DisplayName("유동성 부족 → 부분 체결 후 잔량 CANCELLED")
		void sellMarket_insufficientLiquidity_partialFillThenCancelled() {
			addMakerBuy(10_000, 3);
			Order taker = takerMarketSell(10);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(finalTaker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("MARKET 주문은 잔량이 남아도 bookOps에 Add가 없다")
		void marketOrder_remainingNotAddedToBook() {
			Order taker = takerMarketSell(5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.bookOps()).noneMatch(op -> op instanceof BookOperation.Add);
		}

		@Test
		@DisplayName("updatedOrders에 taker가 항상 포함된다")
		void updatedOrdersAlwaysContainsTaker() {
			Order taker = takerMarketSell(5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.updatedOrders().stream()
				.anyMatch(o -> o.getOrderId().equals(taker.getOrderId()))).isTrue();
		}
	}

	// ── updatedOrders 영속화 계약 ──────────────────────────────────────────

	/**
	 * 부분 체결된 maker가 updatedOrders에서 누락되는 버그를 방지한다.
	 *
	 * <p>이전 구현에서는 완전 체결(FILLED)된 maker만 updatedOrders에 포함시키는 버그가 있었다.
	 * 부분 체결(PARTIALLY_FILLED)된 maker는 상태가 변경됐음에도 누락되어
	 * 영속 DB 전환 시 이중 체결(double execution)을 유발할 수 있다.</p>
	 */
	@Nested
	@DisplayName("updatedOrders 영속화 계약")
	class UpdatedOrdersPersistenceContract {

		@Test
		@DisplayName("LIMIT taker가 maker를 부분 체결하면 부분 체결된 maker가 updatedOrders에 포함되어야 한다")
		void limitOrder_partiallyFilledMaker_mustBeInUpdatedOrders() {
			Order maker = addMakerSell(10_000, 10);
			Order taker = takerBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
		}

		@Test
		@DisplayName("완전 체결된 maker는 updatedOrders에 포함된다 (기존 동작 보호)")
		void limitOrder_fullyFilledMaker_isInUpdatedOrders() {
			Order maker = addMakerSell(10_000, 5);
			Order taker = takerBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("체결에 참여한 taker와 부분 체결된 maker 모두 updatedOrders에 포함되어야 한다")
		void limitOrder_allAffectedOrders_includedInUpdatedOrders() {
			Order maker = addMakerSell(10_000, 10);
			Order taker = takerBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.updatedOrders().stream().map(Order::getOrderId).toList())
				.containsExactlyInAnyOrder(taker.getOrderId(), maker.getOrderId());
		}
	}

	// ── IOC ──────────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("IOC")
	class PlaceLimitOrderIOC {

		private Order iocBuy(long price, long qty) {
			return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.IOC, new Price(price), new Quantity(qty));
		}

		private Order iocSell(long price, long qty) {
			return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.IOC, new Price(price), new Quantity(qty));
		}

		@Test
		@DisplayName("반대 호가 없음 → 체결 없이 즉시 CANCELLED")
		void noOppositeOrder_immediatelyCancelled() {
			Order taker = iocBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("가격 불일치 → 체결 없이 즉시 CANCELLED")
		void priceMismatch_immediatelyCancelled() {
			addMakerSell(11_000, 5);
			Order taker = iocBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("전량 체결 → taker FILLED, Trade 1건 발생")
		void exactMatch_takerFilled() {
			addMakerSell(10_000, 5);
			Order taker = iocBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("부분 체결 — taker qty > maker qty → maker FILLED, taker 잔량 CANCELLED")
		void partialMatch_takerLarger_makerFilledTakerCancelled() {
			Order maker = addMakerSell(10_000, 3);
			Order taker = iocBuy(10_000, 10);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(finalTaker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("부분 체결 — taker qty < maker qty → taker FILLED, maker PARTIALLY_FILLED")
		void partialMatch_takerSmaller_takerFilledMakerPartiallyFilled() {
			Order maker = addMakerSell(10_000, 10);
			Order taker = iocBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			Order finalMaker = findOrder(result, maker.getOrderId());
			assertThat(finalMaker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(finalMaker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("여러 maker와 연속 체결 후 잔량 → taker CANCELLED, trades N건")
		void multipleMatchesThenRemaining_takerCancelled() {
			addMakerSell(10_000, 2);
			addMakerSell(10_000, 3);
			Order taker = iocBuy(10_000, 10);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(2);
			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(finalTaker.getRemaining()).isEqualTo(new Quantity(5));
		}

		@Test
		@DisplayName("IOC taker는 잔량이 있어도 bookOps에 Add가 없다")
		void iocTaker_remainingNotAddedToBook() {
			Order taker = iocBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.bookOps()).noneMatch(op -> op instanceof BookOperation.Add);
		}

		@Test
		@DisplayName("IOC SELL — 반대 호가 없음 → 즉시 CANCELLED")
		void iocSell_noOppositeOrder_immediatelyCancelled() {
			Order taker = iocSell(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("updatedOrders에 IOC taker가 항상 포함된다")
		void updatedOrdersAlwaysContainsTaker() {
			Order taker = iocBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.updatedOrders().stream()
				.anyMatch(o -> o.getOrderId().equals(taker.getOrderId()))).isTrue();
		}
	}

	// ── FOK ──────────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("FOK")
	class PlaceLimitOrderFOK {

		private Order fokBuy(long price, long qty) {
			return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.FOK, new Price(price), new Quantity(qty));
		}

		private Order fokSell(long price, long qty) {
			return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.FOK, new Price(price), new Quantity(qty));
		}

		@Test
		@DisplayName("반대 호가 없음 → 체결 없이 즉시 CANCELLED")
		void noOppositeOrder_immediatelyCancelled() {
			Order taker = fokBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("가격 불일치 → 체결 없이 즉시 CANCELLED")
		void priceMismatch_immediatelyCancelled() {
			addMakerSell(11_000, 5);
			Order taker = fokBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("유동성 부족 (일부만 충족) → 체결 없이 즉시 CANCELLED")
		void insufficientLiquidity_immediatelyCancelled() {
			addMakerSell(10_000, 3);
			Order taker = fokBuy(10_000, 10);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("단일 maker 전량 체결 → Trade 1건, taker FILLED")
		void exactMatch_singleMaker_filled() {
			Order maker = addMakerSell(10_000, 5);
			Order taker = fokBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, maker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("여러 maker와 전량 체결 → Trade N건, taker FILLED")
		void multiMaker_fullFill_filled() {
			addMakerSell(10_000, 3);
			addMakerSell(10_000, 4);
			Order taker = fokBuy(10_000, 7);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(2);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("taker qty < maker qty → taker FILLED, maker PARTIALLY_FILLED")
		void takerSmallerThanMaker_takerFilledMakerPartiallyFilled() {
			Order maker = addMakerSell(10_000, 10);
			Order taker = fokBuy(10_000, 3);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			Order finalMaker = findOrder(result, maker.getOrderId());
			assertThat(finalMaker.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
			assertThat(finalMaker.getRemaining()).isEqualTo(new Quantity(7));
		}

		@Test
		@DisplayName("FOK taker는 체결 여부와 무관하게 bookOps에 Add가 없다")
		void fokTaker_neverAddedToBook() {
			Order taker = fokBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.bookOps()).noneMatch(op -> op instanceof BookOperation.Add);
		}

		@Test
		@DisplayName("FOK SELL — 반대 호가 없음 → 즉시 CANCELLED")
		void fokSell_noOppositeOrder_immediatelyCancelled() {
			Order taker = fokSell(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("updatedOrders에 FOK taker가 항상 포함된다")
		void updatedOrdersAlwaysContainsTaker() {
			Order taker = fokBuy(10_000, 5);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.updatedOrders().stream()
				.anyMatch(o -> o.getOrderId().equals(taker.getOrderId()))).isTrue();
		}
	}

	// ── MARKET BUY (quoteQty 기반) ─────────────────────────────────────────

	@Nested
	@DisplayName("MARKET BUY (quoteQty 기반)")
	class PlaceMarketBuyOrder {

		@Test
		@DisplayName("ask 없음 → 체결 없이 CANCELLED, trades 0건")
		void noAsk_thenCancelled() {
			Order taker = takerMarketBuy(50_000L);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("ask 1건, 예산 완전 소진 → FILLED, trades 1건")
		void singleAsk_fullyConsumed_thenFilled() {
			addMakerSell(10_000, 5); // 10_000 * 5 = 50_000
			Order taker = takerMarketBuy(50_000L);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("ask 여러 건, 예산 소진 → FILLED, trades > 1건")
		void multipleAsks_budgetExhausted_thenFilled() {
			Order ask1 = addMakerSell(10_000, 2);
			Order ask2 = addMakerSell(10_000, 2);
			Order taker = takerMarketBuy(40_000L); // 10_000 * (2 + 2) = 40_000
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(2);
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask1.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask2.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("예산 < ask 최저가 → 체결 없이 CANCELLED, trades 0건")
		void budgetLessThanMinAskPrice_thenCancelled() {
			addMakerSell(10_000, 5);
			Order taker = takerMarketBuy(9_999L); // 예산 9_999 < 최저가 10_000
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).isEmpty();
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("체결 후 예산 잔여 → FILLED (잔여 예산 무관)")
		void partialBudgetRemaining_thenFilled() {
			Order ask = addMakerSell(10_000, 3); // 3건 체결 = 30_000 소진
			Order taker = takerMarketBuy(35_000L); // 5_000 잔여
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			assertThat(result.trades()).hasSize(1);
			assertThat(result.trades().getFirst().quantity()).isEqualTo(new Quantity(3));
			assertThat(findOrder(result, taker.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
			assertThat(findOrder(result, ask.getOrderId()).getStatus()).isEqualTo(OrderStatus.FILLED);
		}

		@Test
		@DisplayName("단일 ask 체결 후 cumQuoteQty/cumBaseQty가 누적된다")
		void singleAsk_accumulates_cumQuoteQtyAndCumBaseQty() {
			addMakerSell(10_000, 5); // 10_000 * 5 = 50_000
			Order taker = takerMarketBuy(50_000L);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getCumQuoteQty().value()).isEqualTo(50_000L);
			assertThat(finalTaker.getCumBaseQty().value()).isEqualTo(5L);
		}

		@Test
		@DisplayName("복수 ask 체결 후 cumQuoteQty/cumBaseQty가 루프마다 누적된다")
		void multipleAsks_accumulates_acrossAllTrades() {
			addMakerSell(10_000, 2); // 20_000
			addMakerSell(10_000, 2); // 20_000
			Order taker = takerMarketBuy(40_000L);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getCumQuoteQty().value()).isEqualTo(40_000L);
			assertThat(finalTaker.getCumBaseQty().value()).isEqualTo(4L);
		}

		@Test
		@DisplayName("체결 없으면 cumQuoteQty/cumBaseQty는 0")
		void noFill_cumFieldsAreZero() {
			Order taker = takerMarketBuy(50_000L);
			PlaceCalculationResult.Accepted result = accepted(place(taker));

			Order finalTaker = findOrder(result, taker.getOrderId());
			assertThat(finalTaker.getCumQuoteQty().value()).isEqualTo(0L);
			assertThat(finalTaker.getCumBaseQty().value()).isEqualTo(0L);
		}
	}

	// ── 취소 (calculateCancel) ────────────────────────────────────────────────

	@Nested
	@DisplayName("취소 (calculateCancel)")
	class CancelOrder {

		@Test
		@DisplayName("NEW 상태 주문 취소 → Cancelled, updatedOrders에 CANCELLED 주문, bookOps에 Remove")
		void newOrder_cancelled_hasCorrectResultAndBookOps() {
			Order maker = addMakerSell(10_000, 5);
			CancelCalculationResult result = cancel(maker);

			assertInstanceOf(CancelCalculationResult.Cancelled.class, result);
			CancelCalculationResult.Cancelled cancelled = (CancelCalculationResult.Cancelled) result;
			assertThat(cancelled.updatedOrders()).hasSize(1);
			assertThat(cancelled.updatedOrders().getFirst().getStatus()).isEqualTo(OrderStatus.CANCELLED);
			assertThat(cancelled.bookOps()).containsExactly(new BookOperation.Remove(maker.getOrderId()));
		}

		@Test
		@DisplayName("PARTIALLY_FILLED 주문 취소 → Cancelled")
		void partiallyFilledOrder_cancelled() {
			Order maker = addMakerSell(10_000, 10);
			Order partiallyFilled = maker.fill(new Quantity(3), new Price(10_000));
			orderBook.replaceOrder(partiallyFilled);

			CancelCalculationResult result = cancel(partiallyFilled);

			assertInstanceOf(CancelCalculationResult.Cancelled.class, result);
			assertThat(((CancelCalculationResult.Cancelled) result)
				.updatedOrders().getFirst().getStatus()).isEqualTo(OrderStatus.CANCELLED);
		}

		@Test
		@DisplayName("같은 가격 레벨에 주문 2개일 때 하나만 취소 → 나머지는 live orderBook에 잔류")
		void twoOrdersSameLevel_cancelOne_otherRemainsInBook() {
			addMakerSell(10_000, 5);
			Order second = addMakerSell(10_000, 3);

			Order first = orderBook.getIndex().values().stream()
				.filter(o -> !o.getOrderId().equals(second.getOrderId()))
				.findFirst().orElseThrow();

			cancel(first); // view만 변경 — live orderBook은 EngineHandler가 처리

			assertThat(orderBook.getIndex().containsKey(second.getOrderId())).isTrue();
		}

		@Test
		@DisplayName("FILLED(final) 주문 취소 → Skipped(ORDER_ALREADY_FINAL)")
		void filledOrder_skipped() {
			Order maker = addMakerSell(10_000, 5);
			Order filled = maker.fill(new Quantity(5), new Price(10_000));

			CancelCalculationResult result = cancel(filled);

			assertInstanceOf(CancelCalculationResult.Skipped.class, result);
			assertThat(((CancelCalculationResult.Skipped) result).reasonCode())
				.isEqualTo(CancelResultCode.ORDER_ALREADY_FINAL);
		}

		@Test
		@DisplayName("CANCELLED(final) 주문 취소 → Skipped(ORDER_ALREADY_FINAL)")
		void cancelledOrder_skipped() {
			Order maker = addMakerSell(10_000, 5);
			Order alreadyCancelled = maker.cancel();

			CancelCalculationResult result = cancel(alreadyCancelled);

			assertInstanceOf(CancelCalculationResult.Skipped.class, result);
			assertThat(((CancelCalculationResult.Skipped) result).reasonCode())
				.isEqualTo(CancelResultCode.ORDER_ALREADY_FINAL);
		}

		@Test
		@DisplayName("Cancelled 결과의 symbol과 acceptedSeq는 원본 주문과 동일하다")
		void cancelledResult_symbolAndAcceptedSeq_matchOriginal() {
			Order maker = addMakerSell(10_000, 5);
			CancelCalculationResult.Cancelled result =
				(CancelCalculationResult.Cancelled) cancel(maker);

			assertThat(result.symbol()).isEqualTo(maker.getSymbol());
			assertThat(result.acceptedSeq()).isEqualTo(maker.getAcceptedSeq());
		}

		@Test
		@DisplayName("다른 account가 취소 요청하면 Rejected(OWNER_MISMATCH)")
		void ownerMismatch_rejected() {
			Order maker = addMakerSell(10_000, 5);

			CancelCalculationResult result = engine.calculateCancel(new CancelCalculationInput(
				view(),
				maker.getSymbol(),
				maker.getOrderId(),
				AccountId.newId(),
				maker
			));

			assertInstanceOf(CancelCalculationResult.Rejected.class, result);
			assertThat(((CancelCalculationResult.Rejected) result).reasonCode())
				.isEqualTo(CancelResultCode.OWNER_MISMATCH);
		}

		@Test
		@DisplayName("다른 symbol로 라우팅되면 Rejected(SYMBOL_MISMATCH)")
		void symbolMismatch_rejected() {
			Order maker = addMakerSell(10_000, 5);

			CancelCalculationResult result = engine.calculateCancel(new CancelCalculationInput(
				view(),
				new Symbol("ETH"),
				maker.getOrderId(),
				maker.getAccountId(),
				maker
			));

			assertInstanceOf(CancelCalculationResult.Rejected.class, result);
			assertThat(((CancelCalculationResult.Rejected) result).reasonCode())
				.isEqualTo(CancelResultCode.SYMBOL_MISMATCH);
		}
	}
}
