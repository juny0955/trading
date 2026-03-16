package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.fixture.OrderFixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.MatchingEngine;
import dev.junyoung.trading.order.domain.service.dto.PlaceResult;

@DisplayName("MatchingEngine 시뮬레이션 — 랜덤 10만 건 불변식 검증")
class MatchingEngineSimulationTest {

    private static final long   SEED        = 42L;
    private static final int    ORDER_COUNT = 100_000;
    private static final long   PRICE_MIN   = 95L;
    private static final long   PRICE_MAX   = 105L;
    private static final long   QTY_MIN     = 1L;
    private static final long   QTY_MAX     = 50L;
    private static final Symbol SYMBOL      = new Symbol("BTC");

    private OrderBook      orderBook;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
        engine    = new MatchingEngine(orderBook);
    }

    /** taker 사이드 기준으로 Trade에서 maker OrderId 추출 */
    private OrderId getMakerId(Trade trade, Side takerSide) {
        return takerSide == Side.BUY ? trade.sellOrderId() : trade.buyOrderId();
    }

    private Order createSupportedMarketOrder(Side side, Symbol symbol, long qty) {
        if (side == Side.BUY) {
            long quoteQty = Math.multiplyExact(qty, PRICE_MAX);
            return OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, symbol, new QuoteQty(quoteQty));
        }
        return OrderFixture.createMarketSell(symbol, new Quantity(qty));
    }

    private PlaceResult placeSupportedMarketOrder(MatchingEngine engine, Order order) {
        if (order.getSide() == Side.BUY) {
            return engine.placeMarketBuyOrderWithQuoteQty(order);
        }
        return engine.placeMarketSellOrder(order);
    }

    @Test
    @DisplayName("랜덤 10만 건 주문 후 가격 우선·FIFO·remaining 음수·상태 전이 위반이 0건이어야 한다")
    void randomSimulation_allInvariantsSatisfied() {
        Random random = new Random(SEED);

        int pricePriorityViolations   = 0;
        int fifoViolations            = 0;
        int stateTransitionViolations = 0;

        List<Order>           allOrders     = new ArrayList<>(ORDER_COUNT);
        Map<OrderId, Integer> submissionSeq = new HashMap<>(ORDER_COUNT * 2);

        // ── 시뮬레이션 루프 ────────────────────────────────────────────────
        for (int i = 0; i < ORDER_COUNT; i++) {

            Side  side  = (random.nextInt(2) == 0) ? Side.BUY : Side.SELL;
            long  price = PRICE_MIN + random.nextLong(PRICE_MAX - PRICE_MIN + 1);
            long  qty   = QTY_MIN   + random.nextLong(QTY_MAX   - QTY_MIN   + 1);
            Order order = OrderFixture.createLimit(side, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));

            submissionSeq.put(order.getOrderId(), i);
            allOrders.add(order);

            // ── 불변식 4: 상태 전이 위반 ──────────────────────────────────
            List<Trade> trades;
            try {
                trades = engine.placeLimitOrder(order).trades();
            } catch (IllegalStateException e) {
                stateTransitionViolations++;
                continue;
            }

            if (trades.size() < 2) continue;

            // ── 불변식 1: 가격 우선 위반 ──────────────────────────────────
            for (int t = 1; t < trades.size(); t++) {
                long prev = trades.get(t - 1).price().value();
                long curr = trades.get(t).price().value();
                // BUY taker: ask 오름차순 체결 → curr >= prev
                if (side == Side.BUY  && curr < prev) pricePriorityViolations++;
                // SELL taker: bid 내림차순 체결 → curr <= prev
                if (side == Side.SELL && curr > prev) pricePriorityViolations++;
            }

            // ── 불변식 2: FIFO 위반 ───────────────────────────────────────
            for (int t = 1; t < trades.size(); t++) {
                if (trades.get(t - 1).price().value()
                        != trades.get(t).price().value()) continue;

                Integer prevSeq = submissionSeq.get(getMakerId(trades.get(t - 1), side));
                Integer currSeq = submissionSeq.get(getMakerId(trades.get(t),     side));
                if (prevSeq != null && currSeq != null && prevSeq > currSeq) {
                    fifoViolations++;
                }
            }
        }

        // ── 불변식 3: remaining 음수 ───────────────────────────────────────
        long negativeRemainingViolations = allOrders.stream()
            .filter(o -> o.getRemaining().value() < 0)
            .count();

        // ── 결과 검증 ──────────────────────────────────────────────────────
        assertThat(pricePriorityViolations)
            .as("가격 우선 위반 건수").isEqualTo(0);
        assertThat(fifoViolations)
            .as("FIFO 위반 건수").isEqualTo(0);
        assertThat(negativeRemainingViolations)
            .as("remaining 음수 건수").isEqualTo(0);
        assertThat(stateTransitionViolations)
            .as("상태 전이 위반 건수").isEqualTo(0);
    }

    @Test
    @DisplayName("LIMIT + MARKET 혼합 10만 건 — remaining 음수·MARKET 최종 상태 위반이 0건이어야 한다")
    void mixedSimulation_allInvariantsSatisfied() {
        Random random = new Random(SEED);

        int stateViolations = 0;
        List<Order> allOrders    = new ArrayList<>(ORDER_COUNT);
        List<Order> marketOrders = new ArrayList<>();

        for (int i = 0; i < ORDER_COUNT; i++) {
            Side side    = (random.nextInt(2) == 0) ? Side.BUY : Side.SELL;
            long qty     = QTY_MIN + random.nextLong(QTY_MAX - QTY_MIN + 1);
            boolean isMarket = random.nextInt(10) < 3;   // 30% MARKET

            Order order;
            if (isMarket) {
                order = createSupportedMarketOrder(side, SYMBOL, qty);
                marketOrders.add(order);
            } else {
                long price = PRICE_MIN + random.nextLong(PRICE_MAX - PRICE_MIN + 1);
                order = OrderFixture.createLimit(side, SYMBOL, TimeInForce.GTC, new Price(price), new Quantity(qty));
            }
            allOrders.add(order);

            try {
                if (isMarket) placeSupportedMarketOrder(engine, order);
                else          engine.placeLimitOrder(order);
            } catch (IllegalStateException e) {
                stateViolations++;
            }
        }

        // 불변식 1: remaining 음수 없음
        long negativeRemaining = allOrders.stream()
            .filter(o -> o.getRemaining().value() < 0)
            .count();

        // 불변식 2: MARKET 주문 최종 상태는 FILLED 또는 CANCELLED 이어야 함
        long marketStateViolations = marketOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.FILLED && o.getStatus() != OrderStatus.CANCELLED)
            .count();

        assertThat(negativeRemaining)
            .as("remaining 음수 건수").isEqualTo(0);
        assertThat(marketStateViolations)
            .as("MARKET 주문 최종 상태 위반 건수 (FILLED 또는 CANCELLED 이어야 함)").isEqualTo(0);
        assertThat(stateViolations)
            .as("상태 전이 위반 건수").isEqualTo(0);
    }

    @Test
    @DisplayName("BTC·ETH 다종목 랜덤 시뮬레이션 — 종목 간 상태 간섭 0건이어야 한다")
    void multiSymbolSimulation_noInterference() {
        Symbol btcSym = new Symbol("BTC");
        Symbol ethSym = new Symbol("ETH");
        MatchingEngine btcEngine = new MatchingEngine(new OrderBook());
        MatchingEngine ethEngine = new MatchingEngine(new OrderBook());

        Random random = new Random(SEED);
        Map<OrderId, Symbol> ownerMap = new HashMap<>();
        int interferenceViolations = 0;
        int stateViolations        = 0;

        for (int i = 0; i < ORDER_COUNT / 2; i++) {
            boolean isBtc  = random.nextBoolean();
            Symbol symbol  = isBtc ? btcSym : ethSym;
            MatchingEngine engine = isBtc ? btcEngine : ethEngine;

            Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
            long qty  = QTY_MIN + random.nextLong(QTY_MAX - QTY_MIN + 1);
            boolean isMarket = random.nextInt(10) < 3;

            Order order;
            PlaceResult result;
            try {
                if (isMarket) {
                    order  = createSupportedMarketOrder(side, symbol, qty);
                    result = placeSupportedMarketOrder(engine, order);
                } else {
                    long price = PRICE_MIN + random.nextLong(PRICE_MAX - PRICE_MIN + 1);
                    order  = OrderFixture.createLimit(side, symbol, TimeInForce.GTC, new Price(price), new Quantity(qty));
                    result = engine.placeLimitOrder(order);
                }
            } catch (IllegalStateException e) {
                stateViolations++;
                continue;
            }
            ownerMap.put(order.getOrderId(), symbol);

            for (Trade trade : result.trades()) {
                Symbol buyOwner  = ownerMap.get(trade.buyOrderId());
                Symbol sellOwner = ownerMap.get(trade.sellOrderId());
                if (buyOwner != null && sellOwner != null && !buyOwner.equals(sellOwner)) {
                    interferenceViolations++;
                }
            }
        }

        assertThat(interferenceViolations).as("종목 간 체결 간섭 건수").isEqualTo(0);
        assertThat(stateViolations).as("상태 전이 위반 건수").isEqualTo(0);
    }
}
