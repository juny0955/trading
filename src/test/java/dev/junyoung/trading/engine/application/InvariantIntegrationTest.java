package dev.junyoung.trading.engine.application;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.engine.application.dto.CancelCalculationResult;
import dev.junyoung.trading.engine.application.dto.PlaceCalculationResult;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.engine.application.service.EngineResultPersistenceService;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.BalanceHoldPolicy;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("InvariantIntegrationTest — 잔고·hold·replay 불변식")
class InvariantIntegrationTest {

    private static final Symbol    SYMBOL = new Symbol("BTC");
    private static final AccountId BUYER  = new AccountId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final AccountId SELLER = new AccountId(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    private static final Asset     KRW    = new Asset("KRW");
    private static final Asset     BTC    = new Asset("BTC");

    @Autowired private DSLContext                   dslContext;
    @Autowired private OrderRepository              orderRepository;
    @Autowired private BalanceRepository            balanceRepository;
    @Autowired private EngineResultPersistenceService engineResultPersistenceService;

    @BeforeEach
    void seedAccounts() {
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, BUYER.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, SELLER.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    /** ACCEPTED → NEW 상태 주문을 DB에 저장하고 반환한다. */
    private Order savedActiveOrder(AccountId accountId, String clientOrderId, long seq,
                                   Side side, long price, long qty) {
        Order order = Order.create(
                OrderId.newId(), accountId, clientOrderId, seq,
                SYMBOL, side, OrderType.LIMIT, TimeInForce.GTC,
                new Price(price), null, new Quantity(qty)).activate();
        orderRepository.save(order);
        return order;
    }

    /** (accountId, asset) 잔고의 available + held 합계 */
    private long total(AccountId accountId, Asset asset) {
        return balanceRepository.findByAccountIdAndAsset(accountId, asset)
                .map(Balance::total).orElse(0L);
    }

    /**
     * open order 목록에서 (accountId, asset) 기준으로 hold 잔량 합계를 재계산한다.
     * remainingHold = holdSpec.amount() - consumedHold
     *   BUY: consumedHold = cumQuoteQty
     *   SELL: consumedHold = cumBaseQty
     */
    private long computedHeldSum(List<Order> openOrders, AccountId accountId, Asset asset) {
        return openOrders.stream()
                .filter(o -> o.getAccountId().equals(accountId))
                .filter(o -> BalanceHoldPolicy.holdSpecFor(o).asset().equals(asset))
                .mapToLong(o -> {
                    long original = BalanceHoldPolicy.holdSpecFor(o).amount();
                    long consumed = o.getSide().isBuy()
                            ? o.getCumQuoteQty().value()
                            : o.getCumBaseQty().value();
                    return original - consumed;
                })
                .sum();
    }

    private void seedStandardBalances(long held, long held1) {
        balanceRepository.save(BUYER, Balance.of(KRW, 0L, held));
        balanceRepository.save(BUYER, Balance.of(BTC, 0L, 0L));
        balanceRepository.save(SELLER, Balance.of(BTC, 0L, held1));
        balanceRepository.save(SELLER, Balance.of(KRW, 0L, 0L));
    }

    // ── 잔고 불변식 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("잔고 불변식 — available + held 합계 보존")
    class BalanceTotalInvariant {

        @Test
        @DisplayName("전량 체결 후 buyer + seller 양측 KRW·BTC total이 보존된다")
        void balanceTotal_preserved_afterFullFill() {
            seedStandardBalances(50_000L, 5L);

            long krwBefore = total(BUYER, KRW) + total(SELLER, KRW);
            long btcBefore = total(BUYER, BTC)  + total(SELLER, BTC);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 5L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 5L);
            Order filledBuy  = buy.fill(new Quantity(5), new Price(10_000));
            Order filledSell = sell.fill(new Quantity(5), new Price(10_000));
            Trade trade = Trade.of(filledBuy, filledSell, new Quantity(5));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(filledBuy, filledSell), List.of(trade), List.of()));

            assertThat(total(BUYER, KRW) + total(SELLER, KRW)).isEqualTo(krwBefore);
            assertThat(total(BUYER, BTC)  + total(SELLER, BTC)).isEqualTo(btcBefore);
        }

        @Test
        @DisplayName("체결 없는 취소 후 KRW total이 보존된다")
        void balanceTotal_preserved_afterCancelWithNoFill() {
            balanceRepository.save(BUYER, Balance.of(KRW, 0L, 50_000L));
            long krwBefore = total(BUYER, KRW);

            Order buy = savedActiveOrder(BUYER, "buy-1", 1L, Side.BUY, 10_000L, 5L);
            Order cancelledBuy = buy.cancel();

            engineResultPersistenceService.persistCancelResult(
                    new CancelCalculationResult.Cancelled(SYMBOL, 1L,
                            List.of(cancelledBuy), List.of()));

            assertThat(total(BUYER, KRW)).isEqualTo(krwBefore);
        }

        @Test
        @DisplayName("부분체결 후 취소 시 buyer + seller 양측 KRW·BTC total이 보존된다")
        void balanceTotal_preserved_afterPartialFillThenCancel() {
            seedStandardBalances(50_000L, 5L);

            long krwBefore = total(BUYER, KRW) + total(SELLER, KRW);
            long btcBefore = total(BUYER, BTC)  + total(SELLER, BTC);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 5L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 5L);
            Order partialBuy  = buy.fill(new Quantity(2), new Price(10_000));
            Order partialSell = sell.fill(new Quantity(2), new Price(10_000));
            Trade trade = Trade.of(partialBuy, partialSell, new Quantity(2));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(partialBuy, partialSell), List.of(trade), List.of()));

            Order cancelledBuy = partialBuy.cancel();
            engineResultPersistenceService.persistCancelResult(
                    new CancelCalculationResult.Cancelled(SYMBOL, 1L,
                            List.of(cancelledBuy), List.of()));

            assertThat(total(BUYER, KRW) + total(SELLER, KRW)).isEqualTo(krwBefore);
            assertThat(total(BUYER, BTC)  + total(SELLER, BTC)).isEqualTo(btcBefore);
        }
    }

    // ── hold 불변식 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("hold 불변식 — 종료 주문 후 held == 0")
    class HoldInvariant {

        @Test
        @DisplayName("BUY 전량 체결 후 buyer.KRW.held == 0")
        void hold_isZero_afterFullFill_buyOrder() {
            seedStandardBalances(50_000L, 5L);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 5L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 5L);
            Order filledBuy  = buy.fill(new Quantity(5), new Price(10_000));
            Order filledSell = sell.fill(new Quantity(5), new Price(10_000));
            Trade trade = Trade.of(filledBuy, filledSell, new Quantity(5));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(filledBuy, filledSell), List.of(trade), List.of()));

            assertThat(balanceRepository.findByAccountIdAndAsset(BUYER, KRW)
                    .orElseThrow().getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("SELL 전량 체결 후 seller.BTC.held == 0")
        void hold_isZero_afterFullFill_sellOrder() {
            seedStandardBalances(50_000L, 5L);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 5L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 5L);
            Order filledBuy  = buy.fill(new Quantity(5), new Price(10_000));
            Order filledSell = sell.fill(new Quantity(5), new Price(10_000));
            Trade trade = Trade.of(filledBuy, filledSell, new Quantity(5));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(filledBuy, filledSell), List.of(trade), List.of()));

            assertThat(balanceRepository.findByAccountIdAndAsset(SELLER, BTC)
                    .orElseThrow().getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("체결 없는 취소 후 buyer.KRW.held == 0")
        void hold_fullyReleased_afterCancelWithNoFill() {
            balanceRepository.save(BUYER, Balance.of(KRW, 0L, 50_000L));

            Order buy = savedActiveOrder(BUYER, "buy-1", 1L, Side.BUY, 10_000L, 5L);
            Order cancelledBuy = buy.cancel();

            engineResultPersistenceService.persistCancelResult(
                    new CancelCalculationResult.Cancelled(SYMBOL, 1L,
                            List.of(cancelledBuy), List.of()));

            assertThat(balanceRepository.findByAccountIdAndAsset(BUYER, KRW)
                    .orElseThrow().getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("부분체결 후 취소 시 buyer.KRW.held == 0 (소비분 + 잔여분 모두 정리)")
        void hold_fullyReleased_afterPartialFillAndCancel() {
            seedStandardBalances(50_000L, 5L);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 5L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 5L);
            Order partialBuy  = buy.fill(new Quantity(2), new Price(10_000));
            Order partialSell = sell.fill(new Quantity(2), new Price(10_000));
            Trade trade = Trade.of(partialBuy, partialSell, new Quantity(2));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(partialBuy, partialSell), List.of(trade), List.of()));

            Order cancelledBuy = partialBuy.cancel();
            engineResultPersistenceService.persistCancelResult(
                    new CancelCalculationResult.Cancelled(SYMBOL, 1L,
                            List.of(cancelledBuy), List.of()));

            assertThat(balanceRepository.findByAccountIdAndAsset(BUYER, KRW)
                    .orElseThrow().getHeld()).isEqualTo(0L);
        }
    }

    // ── replay 불변식 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("replay 불변식 — open order held 합계 == balances.held")
    class ReplayHeldSumInvariant {

        @Test
        @DisplayName("직접 시드된 open order들의 hold 합계가 balance.held와 일치한다")
        void replayHeldSum_matchesBalance_withSeedState() {
            // openBuy1: NEW, price=10_000, qty=5 → hold=50_000, consumed=0 → remainingHold=50_000
            savedActiveOrder(BUYER, "buy-1", 1L, Side.BUY, 10_000L, 5L);

            // openBuy2: PARTIALLY_FILLED, qty=10, fill(qty=3, price=10_000)
            //   → hold=100_000, cumQuote=30_000 → remainingHold=70_000
            Order partialBuy2 = savedActiveOrder(BUYER, "buy-2", 2L, Side.BUY, 10_000L, 10L)
                    .fill(new Quantity(3), new Price(10_000));
            orderRepository.save(partialBuy2);

            // balance.held = 50_000 + 70_000 = 120_000 으로 일치하게 시드
            balanceRepository.save(BUYER, Balance.of(KRW, 10_000L, 120_000L));

            List<Order> openOrders = orderRepository.findOpenOrdersBySymbol(SYMBOL);

            assertThat(computedHeldSum(openOrders, BUYER, KRW))
                    .isEqualTo(balanceRepository.findByAccountIdAndAsset(BUYER, KRW)
                            .orElseThrow().getHeld());
        }

        @Test
        @DisplayName("정산 트랜잭션 후에도 open order held 합계 == balances.held가 유지된다")
        void replayHeldSum_matchesBalance_afterSettlementRoundtrip() {
            // buyer: BUY qty=10, price=10_000 → hold=100_000 KRW
            seedStandardBalances(100_000L, 3L);

            Order buy  = savedActiveOrder(BUYER,  "buy-1",  1L, Side.BUY,  10_000L, 10L);
            Order sell = savedActiveOrder(SELLER, "sell-1", 2L, Side.SELL, 10_000L, 3L);

            // partial fill: qty=3 → buyer.cumQuote=30_000 → remainingHold=70_000
            Order partialBuy = buy.fill(new Quantity(3), new Price(10_000));
            Order filledSell = sell.fill(new Quantity(3), new Price(10_000));
            Trade trade = Trade.of(partialBuy, filledSell, new Quantity(3));

            engineResultPersistenceService.persistPlaceResult(
                    new PlaceCalculationResult.Accepted(SYMBOL, 1L,
                            List.of(partialBuy, filledSell), List.of(trade), List.of()));

            List<Order> openOrders = orderRepository.findOpenOrdersBySymbol(SYMBOL);

            // 정산 후: buyer.KRW.held = 70_000, computedHeldSum = 70_000
            assertThat(computedHeldSum(openOrders, BUYER, KRW))
                    .isEqualTo(balanceRepository.findByAccountIdAndAsset(BUYER, KRW)
                            .orElseThrow().getHeld());
        }
    }
}
