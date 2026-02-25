package dev.junyoung.trading.order.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.order.application.engine.OrderBookCache;
import dev.junyoung.trading.order.application.port.in.result.OrderBookResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBookQueryService")
class OrderBookQueryServiceTest {

    @Mock
    private OrderBookCache orderBookCache;

    @InjectMocks
    private OrderBookQueryService sut;

    @Nested
    @DisplayName("getOrderBookCache()")
    class GetOrderBookCache {

        @Test
        @DisplayName("캐시의 bids/asks가 OrderBookResult에 그대로 담긴다")
        void getOrderBookCache_resultContainsCacheBidsAndAsks() {
            NavigableMap<Long, Long> bids = new TreeMap<>(Comparator.reverseOrder());
            bids.put(10_000L, 5L);
            bids.put(9_000L, 3L);
            NavigableMap<Long, Long> asks = new TreeMap<>();
            asks.put(11_000L, 2L);

            when(orderBookCache.latestBids()).thenReturn(bids);
            when(orderBookCache.latestAsks()).thenReturn(asks);

            OrderBookResult result = sut.getOrderBookCache();

            assertThat(result.bids()).isEqualTo(bids);
            assertThat(result.asks()).isEqualTo(asks);
        }

        @Test
        @DisplayName("캐시가 비어 있으면 bids/asks가 빈 맵으로 반환된다")
        void getOrderBookCache_emptyCache_returnsEmptyMaps() {
            NavigableMap<Long, Long> emptyBids = new TreeMap<>(Comparator.reverseOrder());
            NavigableMap<Long, Long> emptyAsks = new TreeMap<>();

            when(orderBookCache.latestBids()).thenReturn(emptyBids);
            when(orderBookCache.latestAsks()).thenReturn(emptyAsks);

            OrderBookResult result = sut.getOrderBookCache();

            assertThat(result.bids()).isEmpty();
            assertThat(result.asks()).isEmpty();
        }

        @Test
        @DisplayName("bids는 내림차순, asks는 오름차순으로 정렬된 채 반환된다")
        void getOrderBookCache_bidsDescAsksAsc() {
            NavigableMap<Long, Long> bids = new TreeMap<>(Comparator.reverseOrder());
            bids.put(9_000L, 1L);
            bids.put(10_000L, 2L);
            bids.put(8_000L, 3L);
            NavigableMap<Long, Long> asks = new TreeMap<>();
            asks.put(12_000L, 1L);
            asks.put(11_000L, 2L);

            when(orderBookCache.latestBids()).thenReturn(bids);
            when(orderBookCache.latestAsks()).thenReturn(asks);

            OrderBookResult result = sut.getOrderBookCache();

            assertThat(result.bids().firstKey()).isEqualTo(10_000L);
            assertThat(result.asks().firstKey()).isEqualTo(11_000L);
        }
    }
}
