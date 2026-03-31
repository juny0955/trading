package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.engine.application.book.OrderBookSnapshotMapper;
import dev.junyoung.trading.engine.domain.entity.OrderBook;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.fixture.OrderFixture;
import dev.junyoung.trading.shared.adapter.out.cache.OrderBookCache;
import dev.junyoung.trading.shared.domain.entity.OrderBookSnapshot;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.Symbol;

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
        return OrderFixture.createLimit(Side.BUY, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
    }

    private Order activatedSell(long price, long qty) {
        return OrderFixture.createLimit(Side.SELL, BTC, TimeInForce.GTC, new Price(price), new Quantity(qty)).activate();
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
            OrderBookSnapshot snapshot = OrderBookSnapshotMapper.from(book);
            when(orderBookCache.getSnapshot(any(Symbol.class))).thenReturn(snapshot);

            OrderBookResult result = sut.getOrderBookCache("BTC");

            assertThat(result.bids()).hasSize(2).containsEntry(new Price(10_000), new Quantity(5)).containsEntry(new Price(9_000), new Quantity(3));
            assertThat(result.asks()).hasSize(1).containsEntry(new Price(11_000), new Quantity(2));
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
            OrderBookSnapshot snapshot = OrderBookSnapshotMapper.from(book);
            when(orderBookCache.getSnapshot(any(Symbol.class))).thenReturn(snapshot);

            OrderBookResult result = sut.getOrderBookCache("BTC");

            assertThat(result.bids().firstKey()).isEqualTo(new Price(10_000));
            assertThat(result.asks().firstKey()).isEqualTo(new Price(11_000));
        }
    }
}
