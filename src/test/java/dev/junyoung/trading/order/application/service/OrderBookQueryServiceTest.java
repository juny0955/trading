package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.junyoung.trading.order.application.engine.OrderBookCache;
import dev.junyoung.trading.order.application.engine.OrderBookSnapshot;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBookQueryService")
class OrderBookQueryServiceTest {

    @Mock
    private OrderBookCache orderBookCache;

    @InjectMocks
    private OrderBookQueryService sut;

    private static final Symbol BTC = new Symbol("BTC");

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    private Order activatedBuy(long price, long qty) {
        Order order = Order.createLimit(Side.BUY, BTC, new Price(price), new Quantity(qty));
        order.activate();
        return order;
    }

    private Order activatedSell(long price, long qty) {
        Order order = Order.createLimit(Side.SELL, BTC, new Price(price), new Quantity(qty));
        order.activate();
        return order;
    }

    // ── getOrderBookCache() ───────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderBookCache()")
    class GetOrderBookCache {

        @Test
        @DisplayName("캐시의 bids/asks가 OrderBookResult에 그대로 담긴다")
        void getOrderBookCache_resultContainsCacheBidsAndAsks() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(10_000, 5));
            book.add(activatedBuy(9_000, 3));
            book.add(activatedSell(11_000, 2));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);
            when(orderBookCache.getSnapshot(any(Symbol.class))).thenReturn(snapshot);

            OrderBookResult result = sut.getOrderBookCache("BTC");

            assertThat(result.bids()).hasSize(2).containsEntry(10_000L, 5L).containsEntry(9_000L, 3L);
            assertThat(result.asks()).hasSize(1).containsEntry(11_000L, 2L);
        }

        @Test
        @DisplayName("캐시가 비어 있으면 bids/asks가 빈 맵으로 반환된다")
        void getOrderBookCache_emptyCache_returnsEmptyMaps() {
            when(orderBookCache.getSnapshot(any(Symbol.class))).thenReturn(OrderBookSnapshot.EMPTY);

            OrderBookResult result = sut.getOrderBookCache("BTC");

            assertThat(result.bids()).isEmpty();
            assertThat(result.asks()).isEmpty();
        }

        @Test
        @DisplayName("bids는 내림차순, asks는 오름차순으로 정렬된 채 반환된다")
        void getOrderBookCache_bidsDescAsksAsc() {
            OrderBook book = new OrderBook();
            book.add(activatedBuy(9_000, 1));
            book.add(activatedBuy(10_000, 2));
            book.add(activatedBuy(8_000, 3));
            book.add(activatedSell(12_000, 1));
            book.add(activatedSell(11_000, 2));
            OrderBookSnapshot snapshot = OrderBookSnapshot.from(book);
            when(orderBookCache.getSnapshot(any(Symbol.class))).thenReturn(snapshot);

            OrderBookResult result = sut.getOrderBookCache("BTC");

            assertThat(result.bids().firstKey()).isEqualTo(10_000L);
            assertThat(result.asks().firstKey()).isEqualTo(11_000L);
        }
    }
}
