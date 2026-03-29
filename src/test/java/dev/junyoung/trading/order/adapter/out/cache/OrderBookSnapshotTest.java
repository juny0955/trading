package dev.junyoung.trading.order.adapter.out.cache;

import dev.junyoung.trading.order.fixture.OrderFixture;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderBookSnapshot")
class OrderBookSnapshotTest {

    private static final Symbol BTC = new Symbol("BTC");

    private Order activatedBuy(long price, long qty) {
        return OrderFixture.createLimit(Side.BUY, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
    }

    private Order activatedSell(long price, long qty) {
        return OrderFixture.createLimit(Side.SELL, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
    }

    // ── EMPTY ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EMPTY")
    class Empty {

        @Test
        @DisplayName("EMPTY.bids()는 빈 맵이다")
        void empty_bidsIsEmpty() {
            assertThat(OrderBookSnapshot.EMPTY.bids()).isEmpty();
        }

        @Test
        @DisplayName("EMPTY.asks()는 빈 맵이다")
        void empty_asksIsEmpty() {
            assertThat(OrderBookSnapshot.EMPTY.asks()).isEmpty();
        }

        @Test
        @DisplayName("EMPTY.bids()는 수정 불가하다")
        void empty_bidsIsUnmodifiable() {
            assertThatThrownBy(() -> OrderBookSnapshot.EMPTY.bids().put(new Price(1), new Quantity(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("EMPTY.asks()는 수정 불가하다")
        void empty_asksIsUnmodifiable() {
            assertThatThrownBy(() -> OrderBookSnapshot.EMPTY.asks().put(new Price(1), new Quantity(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── from(OrderBook) ───────────────────────────────────────────────────

    @Nested
    @DisplayName("from(OrderBook)")
    class From {

        @Test
        @DisplayName("BUY 주문이 bids에 포함된다")
        void from_buyOrdersInBids() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 5));

            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            assertThat(snapshot.bids()).containsKey(new Price(10_000));
            assertThat(snapshot.asks()).isEmpty();
        }

        @Test
        @DisplayName("SELL 주문이 asks에 포함된다")
        void from_sellOrdersInAsks() {
            OrderBook book = new OrderBook();
            book.add(activatedSell(10_000, 5));

            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            assertThat(snapshot.asks()).containsKey(new Price(10_000));
            assertThat(snapshot.bids()).isEmpty();
        }

        @Test
        @DisplayName("동일 가격 주문의 수량이 합산된다")
        void from_aggregatesQuantityAtSamePrice() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 3));
            book.add(activatedBuy(10_000, 7));

            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            assertThat(snapshot.bids().get(new Price(10_000))).isEqualTo(new Quantity(10));
        }

        @Test
        @DisplayName("bids()는 수정 불가하다")
        void from_bidsIsUnmodifiable() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 1));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            assertThatThrownBy(() -> snapshot.bids().put(new Price(9_000), new Quantity(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("asks()는 수정 불가하다")
        void from_asksIsUnmodifiable() {
            OrderBook book = new OrderBook();
            book.add(activatedSell(10_000, 1));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            assertThatThrownBy(() -> snapshot.asks().put(new Price(11_000), new Quantity(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("빈 OrderBook으로 생성하면 bids·asks 모두 비어 있다")
        void from_emptyOrderBook() {
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(new OrderBook());

            assertThat(snapshot.bids()).isEmpty();
            assertThat(snapshot.asks()).isEmpty();
        }
    }

    // ── 스냅샷 독립성 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("스냅샷 독립성")
    class Isolation {

        @Test
        @DisplayName("스냅샷 생성 후 OrderBook에 주문을 추가해도 스냅샷은 변하지 않는다")
        void snapshot_doesNotChangeWhenOrderBookMutated() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 5));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            // 스냅샷 이후 OrderBook 변경
            book.add(activatedBuy(12_000, 3));

            assertThat(snapshot.bids()).doesNotContainKey(new Price(12_000));
            assertThat(snapshot.bids()).containsKey(new Price(10_000));
        }

        @Test
        @DisplayName("스냅샷 생성 후 OrderBook에서 주문을 poll해도 스냅샷은 변하지 않는다")
        void snapshot_doesNotChangeWhenOrderBookPolled() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 5));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);

            book.poll(Side.BUY);

            assertThat(snapshot.bids()).containsKey(new Price(10_000));
        }
    }
}
