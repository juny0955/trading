package dev.junyoung.trading.order.application.engine;

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
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;

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
            Order order = Order.createLimit(side, SYMBOL, new Price(price), new Quantity(qty));

            submissionSeq.put(order.getOrderId(), i);
            allOrders.add(order);

            // ── 불변식 4: 상태 전이 위반 ──────────────────────────────────
            List<Trade> trades;
            try {
                trades = engine.placeLimitOrder(order);
            } catch (IllegalStateException e) {
                stateTransitionViolations++;
                continue;
            }

            if (trades.size() < 2) continue;

            // ── 불변식 1: 가격 우선 위반 ──────────────────────────────────
            for (int t = 1; t < trades.size(); t++) {
                long prev = trades.get(t - 1).executionPrice().value();
                long curr = trades.get(t).executionPrice().value();
                // BUY taker: ask 오름차순 체결 → curr >= prev
                if (side == Side.BUY  && curr < prev) pricePriorityViolations++;
                // SELL taker: bid 내림차순 체결 → curr <= prev
                if (side == Side.SELL && curr > prev) pricePriorityViolations++;
            }

            // ── 불변식 2: FIFO 위반 ───────────────────────────────────────
            for (int t = 1; t < trades.size(); t++) {
                if (trades.get(t - 1).executionPrice().value()
                        != trades.get(t).executionPrice().value()) continue;

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
}
