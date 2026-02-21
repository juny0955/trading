package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.order.domain.model.Order;
import dev.junyoung.trading.order.domain.model.OrderId;
import dev.junyoung.trading.order.domain.model.Price;
import dev.junyoung.trading.order.domain.model.Quantity;
import dev.junyoung.trading.order.domain.model.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderBook")
class OrderBookTest {

	private OrderBook orderBook;

	@BeforeEach
	void setUp() {
		orderBook = new OrderBook();
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	/** ACCEPTED → activate() → NEW 상태인 BUY 주문 */
	private Order newBuyOrder(long price, long qty) {
		Order order = new Order(Side.BUY, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	/** ACCEPTED → activate() → NEW 상태인 SELL 주문 */
	private Order newSellOrder(long price, long qty) {
		Order order = new Order(Side.SELL, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	// ── add() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("add()")
	class Add {

		@Test
		@DisplayName("BUY 주문을 추가하면 bestBid에서 해당 가격이 조회된다")
		void addBuyOrderUpdatesBestBid() {
			orderBook.add(newBuyOrder(10_000, 5));

			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("SELL 주문을 추가하면 bestAsk에서 해당 가격이 조회된다")
		void addSellOrderUpdatesBestAsk() {
			orderBook.add(newSellOrder(10_000, 5));

			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("동일 가격 레벨에 두 주문을 추가하면 추가 순서(FIFO)로 보관된다")
		void samePriceLevelPreservesFifoOrder() {
			Order first  = newBuyOrder(10_000, 3);
			Order second = newBuyOrder(10_000, 7);
			orderBook.add(first);
			orderBook.add(second);

			assertThat(orderBook.poll(Side.BUY)).contains(first);
			assertThat(orderBook.poll(Side.BUY)).contains(second);
		}

		@Test
		@DisplayName("BUY 주문을 낮은 가격 순으로 추가해도 bestBid는 가장 높은 가격이다")
		void addBuyOrdersAscendingPriceStillShowsHighestBestBid() {
			orderBook.add(newBuyOrder(8_000, 1));
			orderBook.add(newBuyOrder(9_000, 1));
			orderBook.add(newBuyOrder(10_000, 1));

			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("SELL 주문을 높은 가격 순으로 추가해도 bestAsk는 가장 낮은 가격이다")
		void addSellOrdersDescendingPriceStillShowsLowestBestAsk() {
			orderBook.add(newSellOrder(12_000, 1));
			orderBook.add(newSellOrder(11_000, 1));
			orderBook.add(newSellOrder(10_000, 1));

			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}
	}

	// ── poll() ────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("poll()")
	class Poll {

		@Test
		@DisplayName("BUY 호가가 없으면 Optional.empty()를 반환한다")
		void pollEmptyBidsReturnsEmpty() {
			assertThat(orderBook.poll(Side.BUY)).isEmpty();
		}

		@Test
		@DisplayName("SELL 호가가 없으면 Optional.empty()를 반환한다")
		void pollEmptyAsksReturnsEmpty() {
			assertThat(orderBook.poll(Side.SELL)).isEmpty();
		}

		@Test
		@DisplayName("BUY poll: 가장 높은 가격 주문이 먼저 반환된다 (가격 우선)")
		void pollBuyReturnsHighestPriceFirst() {
			Order low  = newBuyOrder(9_000, 1);
			Order high = newBuyOrder(11_000, 1);
			orderBook.add(low);
			orderBook.add(high);

			assertThat(orderBook.poll(Side.BUY)).contains(high);
			assertThat(orderBook.poll(Side.BUY)).contains(low);
		}

		@Test
		@DisplayName("SELL poll: 가장 낮은 가격 주문이 먼저 반환된다 (가격 우선)")
		void pollSellReturnsLowestPriceFirst() {
			Order low  = newSellOrder(9_000, 1);
			Order high = newSellOrder(11_000, 1);
			orderBook.add(high);
			orderBook.add(low);

			assertThat(orderBook.poll(Side.SELL)).contains(low);
			assertThat(orderBook.poll(Side.SELL)).contains(high);
		}

		@Test
		@DisplayName("동일 가격 레벨에서 FIFO 순서로 poll된다")
		void pollSamePriceLevelRespectsFifo() {
			Order first  = newBuyOrder(10_000, 1);
			Order second = newBuyOrder(10_000, 2);
			Order third  = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			orderBook.add(third);

			assertThat(orderBook.poll(Side.BUY)).contains(first);
			assertThat(orderBook.poll(Side.BUY)).contains(second);
			assertThat(orderBook.poll(Side.BUY)).contains(third);
		}

		@Test
		@DisplayName("가격 레벨의 마지막 주문을 poll하면 해당 가격 레벨이 제거되어 bestBid가 갱신된다")
		void pollLastOrderAtLevelRemovesLevelAndUpdatesBestBid() {
			orderBook.add(newBuyOrder(10_000, 1));
			orderBook.add(newBuyOrder(9_000, 1));

			orderBook.poll(Side.BUY); // 10_000 레벨 제거

			assertThat(orderBook.bestBid()).contains(new Price(9_000));
		}

		@Test
		@DisplayName("가격 레벨의 마지막 주문을 poll하면 해당 가격 레벨이 제거되어 bestAsk가 갱신된다")
		void pollLastOrderAtLevelRemovesLevelAndUpdatesBestAsk() {
			orderBook.add(newSellOrder(9_000, 1));
			orderBook.add(newSellOrder(10_000, 1));

			orderBook.poll(Side.SELL); // 9_000 레벨 제거

			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("모든 BUY 주문을 poll하면 bestBid가 empty가 된다")
		void pollAllBuyOrdersLeavesBestBidEmpty() {
			orderBook.add(newBuyOrder(10_000, 1));
			orderBook.poll(Side.BUY);

			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("동일 가격 레벨에 주문이 2개일 때 1개를 poll해도 bestBid는 같은 가격이다")
		void pollOneOfTwoOrdersAtSameLevelPreservesBestBid() {
			orderBook.add(newBuyOrder(10_000, 1));
			orderBook.add(newBuyOrder(10_000, 2));

			orderBook.poll(Side.BUY);

			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}
	}

	// ── bestBid() / bestAsk() ─────────────────────────────────────────────

	@Nested
	@DisplayName("bestBid() / bestAsk()")
	class BestPrice {

		@Test
		@DisplayName("BUY 주문이 없으면 bestBid는 empty다")
		void bestBidIsEmptyWhenNoBuyOrders() {
			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("SELL 주문이 없으면 bestAsk는 empty다")
		void bestAskIsEmptyWhenNoSellOrders() {
			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("여러 BUY 가격 중 bestBid는 가장 높은 가격이다")
		void bestBidIsHighestBuyPrice() {
			orderBook.add(newBuyOrder(8_000, 1));
			orderBook.add(newBuyOrder(10_000, 1));
			orderBook.add(newBuyOrder(9_000, 1));

			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("여러 SELL 가격 중 bestAsk는 가장 낮은 가격이다")
		void bestAskIsLowestSellPrice() {
			orderBook.add(newSellOrder(10_000, 1));
			orderBook.add(newSellOrder(8_000, 1));
			orderBook.add(newSellOrder(9_000, 1));

			assertThat(orderBook.bestAsk()).contains(new Price(8_000));
		}

		@Test
		@DisplayName("SELL 주문이 있어도 bestBid는 영향 받지 않는다")
		void sellOrdersDoNotAffectBestBid() {
			orderBook.add(newSellOrder(9_000, 1));

			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("BUY 주문이 있어도 bestAsk는 영향 받지 않는다")
		void buyOrdersDoNotAffectBestAsk() {
			orderBook.add(newBuyOrder(10_000, 1));

			assertThat(orderBook.bestAsk()).isEmpty();
		}
	}

	// ── remove() ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("remove()")
	class Remove {

		@Test
		@DisplayName("존재하지 않는 orderId로 제거하면 Optional.empty()를 반환한다")
		void removeNonExistentOrderReturnsEmpty() {
			assertThat(orderBook.remove(OrderId.newId())).isEmpty();
		}

		@Test
		@DisplayName("호가창에 있는 BUY 주문을 제거하면 해당 주문이 반환된다")
		void removeExistingBuyOrderReturnsIt() {
			Order buy = newBuyOrder(10_000, 5);
			orderBook.add(buy);

			assertThat(orderBook.remove(buy.getOrderId())).contains(buy);
		}

		@Test
		@DisplayName("호가창에 있는 SELL 주문을 제거하면 해당 주문이 반환된다")
		void removeExistingSellOrderReturnsIt() {
			Order sell = newSellOrder(10_000, 5);
			orderBook.add(sell);

			assertThat(orderBook.remove(sell.getOrderId())).contains(sell);
		}

		@Test
		@DisplayName("동일 orderId로 두 번 제거하면 두 번째는 Optional.empty()를 반환한다")
		void removeSameOrderTwiceReturnsEmptyOnSecond() {
			Order buy = newBuyOrder(10_000, 5);
			orderBook.add(buy);

			orderBook.remove(buy.getOrderId());

			assertThat(orderBook.remove(buy.getOrderId())).isEmpty();
		}

		@Test
		@DisplayName("마지막 BUY 주문 제거 후 bestBid가 empty가 된다")
		void removeLastBuyOrderLeavesBestBidEmpty() {
			Order buy = newBuyOrder(10_000, 1);
			orderBook.add(buy);

			orderBook.remove(buy.getOrderId());

			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("마지막 SELL 주문 제거 후 bestAsk가 empty가 된다")
		void removeLastSellOrderLeavesBestAskEmpty() {
			Order sell = newSellOrder(10_000, 1);
			orderBook.add(sell);

			orderBook.remove(sell.getOrderId());

			assertThat(orderBook.bestAsk()).isEmpty();
		}

		@Test
		@DisplayName("최고가 BUY 레벨의 유일한 주문 제거 후 bestBid가 다음 가격으로 갱신된다")
		void removeBestBidOnlyOrderUpdatesNextBestBid() {
			Order best = newBuyOrder(10_000, 1);
			orderBook.add(best);
			orderBook.add(newBuyOrder(9_000, 1));

			orderBook.remove(best.getOrderId());

			assertThat(orderBook.bestBid()).contains(new Price(9_000));
		}

		@Test
		@DisplayName("최저가 SELL 레벨의 유일한 주문 제거 후 bestAsk가 다음 가격으로 갱신된다")
		void removeBestAskOnlyOrderUpdatesNextBestAsk() {
			Order best = newSellOrder(9_000, 1);
			orderBook.add(best);
			orderBook.add(newSellOrder(10_000, 1));

			orderBook.remove(best.getOrderId());

			assertThat(orderBook.bestAsk()).contains(new Price(10_000));
		}

		@Test
		@DisplayName("동일 가격 레벨 첫 번째 주문 제거 후 두 번째 주문이 poll된다")
		void removeFirstOrderOfLevelLeavesSecondOrderPollable() {
			Order first  = newBuyOrder(10_000, 3);
			Order second = newBuyOrder(10_000, 7);
			orderBook.add(first);
			orderBook.add(second);

			orderBook.remove(first.getOrderId());

			assertThat(orderBook.poll(Side.BUY)).contains(second);
		}

		@Test
		@DisplayName("동일 가격 레벨 두 번째 주문 제거 후 첫 번째 주문이 poll된다")
		void removeSecondOrderOfLevelLeavesFirstOrderPollable() {
			Order first  = newBuyOrder(10_000, 3);
			Order second = newBuyOrder(10_000, 7);
			orderBook.add(first);
			orderBook.add(second);

			orderBook.remove(second.getOrderId());

			assertThat(orderBook.poll(Side.BUY)).contains(first);
		}

		@Test
		@DisplayName("동일 가격 레벨에서 중간 주문 제거 후 나머지 주문의 FIFO 순서가 유지된다")
		void removeMiddleOrderPreservesFifoForRemainingOrders() {
			Order first  = newBuyOrder(10_000, 1);
			Order middle = newBuyOrder(10_000, 2);
			Order last   = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(middle);
			orderBook.add(last);

			orderBook.remove(middle.getOrderId());

			assertThat(orderBook.poll(Side.BUY)).contains(first);
			assertThat(orderBook.poll(Side.BUY)).contains(last);
		}

		@Test
		@DisplayName("비최우선 가격 레벨의 주문 제거 후 bestBid는 변경되지 않는다")
		void removeNonBestBidOrderDoesNotChangeBestBid() {
			orderBook.add(newBuyOrder(10_000, 1));
			Order lower = newBuyOrder(9_000, 1);
			orderBook.add(lower);

			orderBook.remove(lower.getOrderId());

			assertThat(orderBook.bestBid()).contains(new Price(10_000));
		}
	}

	// ── 인덱스 정합성 (poll + remove) ─────────────────────────────────────

	@Nested
	@DisplayName("인덱스 정합성")
	class IndexConsistency {

		@Test
		@DisplayName("poll()로 꺼낸 주문을 remove()하면 Optional.empty()를 반환한다 (이중 제거 방지)")
		void removeAfterPollReturnsEmpty() {
			Order buy = newBuyOrder(10_000, 1);
			orderBook.add(buy);

			orderBook.poll(Side.BUY);

			assertThat(orderBook.remove(buy.getOrderId())).isEmpty();
		}

		@Test
		@DisplayName("poll() 후 같은 가격 레벨의 다른 주문은 remove()로 정상 제거된다")
		void removeOtherOrderAfterPollSameLevelWorks() {
			Order first  = newBuyOrder(10_000, 1);
			Order second = newBuyOrder(10_000, 2);
			orderBook.add(first);
			orderBook.add(second);

			orderBook.poll(Side.BUY); // first 제거

			assertThat(orderBook.remove(second.getOrderId())).contains(second);
			assertThat(orderBook.bestBid()).isEmpty();
		}

		@Test
		@DisplayName("remove() 후 해당 주문은 poll()에서 나오지 않는다")
		void pollAfterRemoveDoesNotReturnRemovedOrder() {
			Order first  = newBuyOrder(10_000, 1);
			Order second = newBuyOrder(10_000, 2);
			orderBook.add(first);
			orderBook.add(second);

			orderBook.remove(first.getOrderId());

			assertThat(orderBook.poll(Side.BUY)).contains(second);
			assertThat(orderBook.poll(Side.BUY)).isEmpty();
		}
	}

	// ── 가격 우선순위 ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("가격 우선순위")
	class PriceOrdering {

		@Test
		@DisplayName("BUY: 높은 가격 → 낮은 가격 순으로 poll된다")
		void buyPollOrderIsDescendingByPrice() {
			orderBook.add(newBuyOrder(9_000, 1));
			orderBook.add(newBuyOrder(11_000, 1));
			orderBook.add(newBuyOrder(10_000, 1));

			assertThat(orderBook.poll(Side.BUY).map(Order::getPrice)).contains(new Price(11_000));
			assertThat(orderBook.poll(Side.BUY).map(Order::getPrice)).contains(new Price(10_000));
			assertThat(orderBook.poll(Side.BUY).map(Order::getPrice)).contains(new Price(9_000));
		}

		@Test
		@DisplayName("SELL: 낮은 가격 → 높은 가격 순으로 poll된다")
		void sellPollOrderIsAscendingByPrice() {
			orderBook.add(newSellOrder(11_000, 1));
			orderBook.add(newSellOrder(9_000, 1));
			orderBook.add(newSellOrder(10_000, 1));

			assertThat(orderBook.poll(Side.SELL).map(Order::getPrice)).contains(new Price(9_000));
			assertThat(orderBook.poll(Side.SELL).map(Order::getPrice)).contains(new Price(10_000));
			assertThat(orderBook.poll(Side.SELL).map(Order::getPrice)).contains(new Price(11_000));
		}

		@Test
		@DisplayName("BUY: 나중에 추가된 더 높은 가격이 먼저 체결된다 (가격 우선 > 시간 우선)")
		void buyLaterHigherPriceWinsOverEarlierLowerPrice() {
			Order earlierLow = newBuyOrder(9_000, 1);
			Order laterHigh  = newBuyOrder(10_000, 1);
			orderBook.add(earlierLow);
			orderBook.add(laterHigh);

			assertThat(orderBook.poll(Side.BUY)).contains(laterHigh);
		}

		@Test
		@DisplayName("SELL: 나중에 추가된 더 낮은 가격이 먼저 체결된다 (가격 우선 > 시간 우선)")
		void sellLaterLowerPriceWinsOverEarlierHigherPrice() {
			Order earlierHigh = newSellOrder(10_000, 1);
			Order laterLow    = newSellOrder(9_000, 1);
			orderBook.add(earlierHigh);
			orderBook.add(laterLow);

			assertThat(orderBook.poll(Side.SELL)).contains(laterLow);
		}
	}

	// ── FIFO (시간 우선) ───────────────────────────────────────────────────

	@Nested
	@DisplayName("FIFO (시간 우선)")
	class FifoOrdering {

		@Test
		@DisplayName("동일 BUY 가격: 먼저 추가된 주문이 먼저 poll된다")
		void sameBuyPriceRespectsFifo() {
			Order first  = newBuyOrder(10_000, 1);
			Order second = newBuyOrder(10_000, 2);
			Order third  = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			orderBook.add(third);

			assertThat(orderBook.poll(Side.BUY)).contains(first);
			assertThat(orderBook.poll(Side.BUY)).contains(second);
			assertThat(orderBook.poll(Side.BUY)).contains(third);
		}

		@Test
		@DisplayName("동일 SELL 가격: 먼저 추가된 주문이 먼저 poll된다")
		void sameSellPriceRespectsFifo() {
			Order first  = newSellOrder(10_000, 1);
			Order second = newSellOrder(10_000, 2);
			Order third  = newSellOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			orderBook.add(third);

			assertThat(orderBook.poll(Side.SELL)).contains(first);
			assertThat(orderBook.poll(Side.SELL)).contains(second);
			assertThat(orderBook.poll(Side.SELL)).contains(third);
		}
	}
}