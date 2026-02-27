package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.NavigableMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OrderBookCache} 단위 테스트.
 *
 * <p>engine-thread가 {@link OrderBookCache#update}로 스냅샷을 교체하고, HTTP 스레드가
 * {@link OrderBookCache#latestBids}/{@link OrderBookCache#latestAsks}로 읽는 시나리오를 단일 스레드에서 검증한다.
 * 동시성 정확성은 통합/스트레스 테스트에서 별도 검증한다.</p>
 */
@DisplayName("OrderBookCache")
class OrderBookCacheTest {

	private OrderBookCache cache;

	private static final Symbol BTC = new Symbol("BTC");
	private static final Symbol ETH = new Symbol("ETH");

	@BeforeEach
	void setUp() {
		cache = new OrderBookCache();
	}

	// ── 헬퍼 ──────────────────────────────────────────────────────────────

	private Order activatedBuy(Symbol symbol, long price, long qty) {
		Order order = Order.createLimit(Side.BUY, symbol, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	private Order activatedSell(Symbol symbol, long price, long qty) {
		Order order = Order.createLimit(Side.SELL, symbol, new Price(price), new Quantity(qty));
		order.activate();
		return order;
	}

	// ── 업데이트 전 기본값 ────────────────────────────────────────────────

	@Nested
	@DisplayName("업데이트 전 기본값")
	class BeforeUpdate {

		@Test
		@DisplayName("등록되지 않은 심볼의 latestBids()는 빈 맵을 반환한다 (NPE 없음)")
		void latestBids_unknownSymbol_returnsEmptyMap() {
			NavigableMap<Long, Long> bids = cache.latestBids(new Symbol("UNKNOWN"));

			assertThat(bids).isNotNull().isEmpty();
		}

		@Test
		@DisplayName("등록되지 않은 심볼의 latestAsks()는 빈 맵을 반환한다 (NPE 없음)")
		void latestAsks_unknownSymbol_returnsEmptyMap() {
			NavigableMap<Long, Long> asks = cache.latestAsks(new Symbol("UNKNOWN"));

			assertThat(asks).isNotNull().isEmpty();
		}
	}

	// ── latestBids() ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("latestBids()")
	class LatestBids {

		@Test
		@DisplayName("update 후 BUY 주문 가격이 bids에 포함된다")
		void latestBids_afterUpdate_containsBidPrice() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));

			cache.update(BTC, book);

			assertThat(cache.latestBids(BTC)).containsKey(10_000L);
		}

		@Test
		@DisplayName("bids는 가격 내림차순으로 정렬된다 (최고가 firstKey)")
		void latestBids_descendingOrder() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 9_000, 1));
			book.add(activatedBuy(BTC, 11_000, 1));
			book.add(activatedBuy(BTC, 10_000, 1));

			cache.update(BTC, book);

			NavigableMap<Long, Long> bids = cache.latestBids(BTC);
			assertThat(bids.firstKey()).isEqualTo(11_000L);
			assertThat(bids.lastKey()).isEqualTo(9_000L);
		}

		@Test
		@DisplayName("같은 가격 레벨의 여러 BUY 주문은 수량이 합산된다")
		void latestBids_aggregatesQuantitiesAtSamePrice() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));
			book.add(activatedBuy(BTC, 10_000, 3));
			book.add(activatedBuy(BTC, 10_000, 2));

			cache.update(BTC, book);

			assertThat(cache.latestBids(BTC).get(10_000L)).isEqualTo(10L);
		}

		@Test
		@DisplayName("SELL 주문은 bids에 포함되지 않는다")
		void latestBids_doesNotContainSellOrders() {
			OrderBook book = new OrderBook();
			book.add(activatedSell(BTC, 10_000, 5));

			cache.update(BTC, book);

			assertThat(cache.latestBids(BTC)).isEmpty();
		}
	}

	// ── latestAsks() ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("latestAsks()")
	class LatestAsks {

		@Test
		@DisplayName("update 후 SELL 주문 가격이 asks에 포함된다")
		void latestAsks_afterUpdate_containsAskPrice() {
			OrderBook book = new OrderBook();
			book.add(activatedSell(BTC, 10_000, 5));

			cache.update(BTC, book);

			assertThat(cache.latestAsks(BTC)).containsKey(10_000L);
		}

		@Test
		@DisplayName("asks는 가격 오름차순으로 정렬된다 (최저가 firstKey)")
		void latestAsks_ascendingOrder() {
			OrderBook book = new OrderBook();
			book.add(activatedSell(BTC, 11_000, 1));
			book.add(activatedSell(BTC, 9_000, 1));
			book.add(activatedSell(BTC, 10_000, 1));

			cache.update(BTC, book);

			NavigableMap<Long, Long> asks = cache.latestAsks(BTC);
			assertThat(asks.firstKey()).isEqualTo(9_000L);
			assertThat(asks.lastKey()).isEqualTo(11_000L);
		}

		@Test
		@DisplayName("같은 가격 레벨의 여러 SELL 주문은 수량이 합산된다")
		void latestAsks_aggregatesQuantitiesAtSamePrice() {
			OrderBook book = new OrderBook();
			book.add(activatedSell(BTC, 10_000, 4));
			book.add(activatedSell(BTC, 10_000, 6));

			cache.update(BTC, book);

			assertThat(cache.latestAsks(BTC).get(10_000L)).isEqualTo(10L);
		}

		@Test
		@DisplayName("BUY 주문은 asks에 포함되지 않는다")
		void latestAsks_doesNotContainBuyOrders() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));

			cache.update(BTC, book);

			assertThat(cache.latestAsks(BTC)).isEmpty();
		}
	}

	// ── 스냅샷 교체 ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("스냅샷 교체")
	class SnapshotReplacement {

		@Test
		@DisplayName("두 번째 update 후 최신 bids 스냅샷이 반환된다")
		void latestBids_returnsLatestSnapshot() {
			OrderBook book1 = new OrderBook();
			book1.add(activatedBuy(BTC, 10_000, 5));
			cache.update(BTC, book1);

			OrderBook book2 = new OrderBook();
			book2.add(activatedBuy(BTC, 12_000, 3));
			cache.update(BTC, book2);

			NavigableMap<Long, Long> bids = cache.latestBids(BTC);
			assertThat(bids).containsKey(12_000L);
			assertThat(bids).doesNotContainKey(10_000L);
		}

		@Test
		@DisplayName("두 번째 update 후 최신 asks 스냅샷이 반환된다")
		void latestAsks_returnsLatestSnapshot() {
			OrderBook book1 = new OrderBook();
			book1.add(activatedSell(BTC, 10_000, 5));
			cache.update(BTC, book1);

			OrderBook book2 = new OrderBook();
			book2.add(activatedSell(BTC, 8_000, 3));
			cache.update(BTC, book2);

			NavigableMap<Long, Long> asks = cache.latestAsks(BTC);
			assertThat(asks).containsKey(8_000L);
			assertThat(asks).doesNotContainKey(10_000L);
		}

		@Test
		@DisplayName("빈 OrderBook으로 update하면 bids·asks 모두 빈 맵이 반환된다")
		void update_emptyOrderBook_returnsEmptySnapshot() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));
			cache.update(BTC, book);

			cache.update(BTC, new OrderBook());

			assertThat(cache.latestBids(BTC)).isEmpty();
			assertThat(cache.latestAsks(BTC)).isEmpty();
		}

		@Test
		@DisplayName("latestBids·latestAsks는 동일한 스냅샷에서 꺼낸 값이다 (bids·asks 일관성)")
		void latestBids_and_latestAsks_comeFromSameSnapshot() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 9_000, 2));
			book.add(activatedSell(BTC, 11_000, 3));
			cache.update(BTC, book);

			NavigableMap<Long, Long> bids = cache.latestBids(BTC);
			NavigableMap<Long, Long> asks = cache.latestAsks(BTC);

			assertThat(bids).containsKey(9_000L);
			assertThat(asks).containsKey(11_000L);
		}
	}

	// ── 반환 맵 불변성 ────────────────────────────────────────────────────

	@Nested
	@DisplayName("반환 맵 불변성")
	class Immutability {

		@Test
		@DisplayName("latestBids() 반환 맵은 수정 불가하다")
		void latestBids_returnsUnmodifiableMap() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));
			cache.update(BTC, book);

			NavigableMap<Long, Long> bids = cache.latestBids(BTC);

			assertThatThrownBy(() -> bids.put(9_000L, 1L))
					.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		@DisplayName("latestAsks() 반환 맵은 수정 불가하다")
		void latestAsks_returnsUnmodifiableMap() {
			OrderBook book = new OrderBook();
			book.add(activatedSell(BTC, 10_000, 5));
			cache.update(BTC, book);

			NavigableMap<Long, Long> asks = cache.latestAsks(BTC);

			assertThatThrownBy(() -> asks.put(11_000L, 1L))
					.isInstanceOf(UnsupportedOperationException.class);
		}
	}

	// ── 심볼 독립성 ──────────────────────────────────────────────────────

	@Nested
	@DisplayName("심볼 독립성")
	class SymbolIsolation {

		@Test
		@DisplayName("BTC와 ETH 스냅샷은 서로 독립적이다")
		void update_differentSymbols_areIndependent() {
			OrderBook btcBook = new OrderBook();
			btcBook.add(activatedBuy(BTC, 30_000, 2));
			cache.update(BTC, btcBook);

			OrderBook ethBook = new OrderBook();
			ethBook.add(activatedBuy(ETH, 2_000, 10));
			cache.update(ETH, ethBook);

			assertThat(cache.latestBids(BTC)).containsKey(30_000L).doesNotContainKey(2_000L);
			assertThat(cache.latestBids(ETH)).containsKey(2_000L).doesNotContainKey(30_000L);
		}

		@Test
		@DisplayName("BTC 스냅샷 갱신이 ETH 스냅샷에 영향을 주지 않는다")
		void update_btc_doesNotAffectEth() {
			OrderBook ethBook = new OrderBook();
			ethBook.add(activatedBuy(ETH, 2_000, 5));
			cache.update(ETH, ethBook);

			cache.update(BTC, new OrderBook()); // BTC 갱신

			assertThat(cache.latestBids(ETH)).containsKey(2_000L);
		}

		@Test
		@DisplayName("미등록 심볼 조회는 등록된 심볼 스냅샷에 영향을 주지 않는다")
		void latestBids_unknownSymbol_doesNotAffectRegisteredSymbol() {
			OrderBook book = new OrderBook();
			book.add(activatedBuy(BTC, 10_000, 5));
			cache.update(BTC, book);

			cache.latestBids(new Symbol("UNKNOWN")); // 부수 효과 없어야 함

			assertThat(cache.latestBids(BTC)).containsKey(10_000L);
		}
	}
}
