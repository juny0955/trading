package dev.junyoung.trading.order.domain.service.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.junyoung.trading.order.application.engine.OrderBookViewFactory;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("OrderBookView")
class OrderBookViewTest {

	private OrderBook orderBook;

	private static final Symbol SYMBOL = new Symbol("BTC");

	@BeforeEach
	void setUp() {
		orderBook = new OrderBook();
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	private OrderBookView view() {
		return OrderBookViewFactory.create(orderBook);
	}

	/** ACCEPTED → activate() → NEW 상태인 BUY 주문 */
	private Order newBuyOrder(long price, long qty) {
		return OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
	}

	/** ACCEPTED → activate() → NEW 상태인 SELL 주문 */
	private Order newSellOrder(long price, long qty) {
		return OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
	}

	// ── add() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("add()")
	class Add {

		@Test
		@DisplayName("BUY 주문을 추가하면 peek(BUY)에서 조회된다")
		void addBuyOrder_peekReturnsIt() {
			OrderBookView view = view();
			Order buy = newBuyOrder(10_000, 5);

			view.add(buy);

			assertThat(view.peek(Side.BUY)).contains(buy);
		}

		@Test
		@DisplayName("SELL 주문을 추가하면 peek(SELL)에서 조회된다")
		void addSellOrder_peekReturnsIt() {
			OrderBookView view = view();
			Order sell = newSellOrder(10_000, 5);

			view.add(sell);

			assertThat(view.peek(Side.SELL)).contains(sell);
		}

		@Test
		@DisplayName("동일 가격 BUY 2개를 추가하면 FIFO 순서로 poll된다")
		void samePriceBuyOrders_fifoOrder() {
			OrderBookView view = view();
			Order first = newBuyOrder(10_000, 3);
			Order second = newBuyOrder(10_000, 7);

			view.add(first);
			view.add(second);

			view.poll(Side.BUY);
			assertThat(view.peek(Side.BUY)).contains(second);
		}
	}

	// ── peek() ────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("peek()")
	class Peek {

		@Test
		@DisplayName("빈 book에서 peek(BUY)는 Optional.empty()를 반환한다")
		void emptyBook_peekBuyReturnsEmpty() {
			assertThat(view().peek(Side.BUY)).isEmpty();
		}

		@Test
		@DisplayName("빈 book에서 peek(SELL)은 Optional.empty()를 반환한다")
		void emptyBook_peekSellReturnsEmpty() {
			assertThat(view().peek(Side.SELL)).isEmpty();
		}

		@Test
		@DisplayName("BUY 여러 가격 중 최고가 주문을 반환한다")
		void multipleBuyPrices_returnsBestBid() {
			Order low = newBuyOrder(9_000, 5);
			Order high = newBuyOrder(11_000, 3);
			orderBook.add(low);
			orderBook.add(high);

			assertThat(view().peek(Side.BUY)).contains(high);
		}

		@Test
		@DisplayName("SELL 여러 가격 중 최저가 주문을 반환한다")
		void multipleSellPrices_returnsBestAsk() {
			Order low = newSellOrder(9_000, 5);
			Order high = newSellOrder(11_000, 3);
			orderBook.add(low);
			orderBook.add(high);

			assertThat(view().peek(Side.SELL)).contains(low);
		}

		@Test
		@DisplayName("index에 없는 stale ID는 건너뛰고 다음 유효 주문을 반환한다")
		void staleId_skipsAndReturnsNextValid() {
			Order first = newBuyOrder(10_000, 5);
			Order second = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			OrderBookView view = view();

			// first를 index에서만 제거 → Deque에는 stale ID로 남음
			view.removeInIndex(first.getOrderId());

			assertThat(view.peek(Side.BUY)).contains(second);
		}
	}

	// ── poll() ────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("poll()")
	class Poll {

		@Test
		@DisplayName("빈 book에서 poll()은 예외 없이 무시된다")
		void emptyBook_pollIsNoOp() {
			assertThatCode(() -> view().poll(Side.BUY)).doesNotThrowAnyException();
		}

		@Test
		@DisplayName("BUY poll 후 peek 결과가 다음 우선순위로 변경된다")
		void pollBuy_peekReturnsNext() {
			Order high = newBuyOrder(11_000, 3);
			Order low = newBuyOrder(9_000, 5);
			orderBook.add(high);
			orderBook.add(low);
			OrderBookView view = view();

			view.poll(Side.BUY);

			assertThat(view.peek(Side.BUY)).contains(low);
		}

		@Test
		@DisplayName("SELL poll 후 peek 결과가 다음 우선순위로 변경된다")
		void pollSell_peekReturnsNext() {
			Order low = newSellOrder(9_000, 5);
			Order high = newSellOrder(11_000, 3);
			orderBook.add(low);
			orderBook.add(high);
			OrderBookView view = view();

			view.poll(Side.SELL);

			assertThat(view.peek(Side.SELL)).contains(high);
		}

		@Test
		@DisplayName("stale ID는 건너뛰고 유효한 주문을 소비한다")
		void staleId_skipsAndConsumesValidOrder() {
			Order first = newBuyOrder(10_000, 5);
			Order second = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			OrderBookView view = view();

			view.removeInIndex(first.getOrderId()); // first를 stale로 만듦
			view.poll(Side.BUY);                    // stale 건너뛰고 second 소비

			assertThat(view.peek(Side.BUY)).isEmpty();
		}
	}

	// ── replaceInIndex() ──────────────────────────────────────────────────

	@Nested
	@DisplayName("replaceInIndex()")
	class ReplaceInIndex {

		@Test
		@DisplayName("replaceInIndex 후 peek은 새 인스턴스를 반환한다")
		void replaceInIndex_peekReturnsUpdatedOrder() {
			Order original = newBuyOrder(10_000, 10);
			orderBook.add(original);
			OrderBookView view = view();

			Order updated = original.fill(new Quantity(3), new Price(10_000));
			view.replaceInIndex(updated);

			assertThat(view.peek(Side.BUY)).contains(updated);
		}
	}

	// ── removeInIndex() ───────────────────────────────────────────────────

	@Nested
	@DisplayName("removeInIndex()")
	class RemoveInIndex {

		@Test
		@DisplayName("removeInIndex 후 peek은 다음 주문을 반환한다")
		void removeInIndex_peekReturnsNext() {
			Order first = newBuyOrder(10_000, 5);
			Order second = newBuyOrder(10_000, 3);
			orderBook.add(first);
			orderBook.add(second);
			OrderBookView view = view();

			view.removeInIndex(first.getOrderId());

			assertThat(view.peek(Side.BUY)).contains(second);
		}

		@Test
		@DisplayName("유일한 주문을 removeInIndex 후 peek은 Optional.empty()를 반환한다")
		void removeInIndex_onlyOrder_peekReturnsEmpty() {
			Order order = newBuyOrder(10_000, 5);
			orderBook.add(order);
			OrderBookView view = view();

			view.removeInIndex(order.getOrderId());

			assertThat(view.peek(Side.BUY)).isEmpty();
		}
	}

	// ── totalAvailableQty() ───────────────────────────────────────────────

	@Nested
	@DisplayName("totalAvailableQty()")
	class TotalAvailableQty {

		@Test
		@DisplayName("빈 book에서 SELL side 총 잔량은 0이다")
		void emptyBook_sellSide_returnsZero() {
			assertThat(view().totalAvailableQty(Side.SELL, new Price(10_000)).value()).isZero();
		}

		@Test
		@DisplayName("SELL maker: limitPrice 이하 레벨의 수량을 합산한다")
		void sellMaker_sumsQtyAtOrBelowLimitPrice() {
			orderBook.add(newSellOrder(9_000, 5));   // 포함
			orderBook.add(newSellOrder(10_000, 3));  // 포함 (inclusive)
			orderBook.add(newSellOrder(11_000, 7));  // 제외

			long qty = view().totalAvailableQty(Side.SELL, new Price(10_000)).value();

			assertThat(qty).isEqualTo(8); // 5 + 3
		}

		@Test
		@DisplayName("SELL maker: limitPrice 초과 레벨은 합산에서 제외된다")
		void sellMaker_excludesQtyAboveLimitPrice() {
			orderBook.add(newSellOrder(11_000, 7));

			long qty = view().totalAvailableQty(Side.SELL, new Price(10_000)).value();

			assertThat(qty).isZero();
		}
	}
}
