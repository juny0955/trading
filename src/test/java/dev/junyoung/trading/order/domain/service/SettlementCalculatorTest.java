package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.dto.SettlementInput;
import dev.junyoung.trading.order.domain.service.dto.SettlementResult;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SettlementCalculator")
class SettlementCalculatorTest {

    private static final Symbol BTC = new Symbol("BTC");
    private static final Asset KRW = new Asset("KRW");
    private static final Asset BTC_ASSET = new Asset("BTC");
    private static final Price P_9_000 = new Price(9_000);
    private static final Price P_10_000 = new Price(10_000);
    private static final Price P_11_000 = new Price(11_000);

    private static final AccountId BUYER = accountId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final AccountId SELLER = accountId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final AccountId SELLER2 = accountId("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    @DisplayName("빈 입력이면 delta가 없다")
    void emptyInput_returnsEmptyResult() {
        SettlementResult result = SettlementCalculator.settle(new SettlementInput(List.of(), List.of()));

        assertThat(result.balanceDeltas()).isEmpty();
    }

    @Test
    @DisplayName("미종결 주문만 있으면 환불도 체결 정산도 없다")
    void openOrdersOnly_returnsEmptyResult() {
        Order openBuy = activatedLimit(BUYER, Side.BUY, P_10_000, 3);

        SettlementResult result = SettlementCalculator.settle(new SettlementInput(List.of(openBuy), List.of()));

        assertThat(result.balanceDeltas()).isEmpty();
    }

    @Nested
    @DisplayName("체결 정산")
    class TradeSettlement {

        @Test
        @DisplayName("정확히 전량 체결되면 trade delta만 반영된다")
        void exactFill_appliesTradeDeltaOnly() {
            Order buy = filledLimit(BUYER, Side.BUY, P_10_000, 1, P_10_000, 1);
            Order sell = filledLimit(SELLER, Side.SELL, P_10_000, 1, P_10_000, 1);
            Trade trade = trade(buy, sell, 1);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buy, sell), trade));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 0L, -10_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 1L, 0L);
            assertDelta(deltas, SELLER, BTC_ASSET, 0L, -1L);
            assertDelta(deltas, SELLER, KRW, 10_000L, 0L);
            assertThat(deltas).hasSize(4);
        }

        @Test
        @DisplayName("LIMIT BUY 가격개선이면 종료 시 차액이 available로 반환된다")
        void limitBuy_priceImprovement_refundsDifferenceOnFinalization() {
            Order buy = filledLimit(BUYER, Side.BUY, P_11_000, 2, P_10_000, 2);
            Order sell = filledLimit(SELLER, Side.SELL, P_10_000, 2, P_10_000, 2);
            Trade trade = trade(buy, sell, 2);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buy, sell), trade));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 2_000L, -22_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 2L, 0L);
            assertDelta(deltas, SELLER, BTC_ASSET, 0L, -2L);
            assertDelta(deltas, SELLER, KRW, 20_000L, 0L);
        }

        @Test
        @DisplayName("복수 trade는 account-asset 기준으로 누적 집계된다")
        void multipleTrades_areAggregatedByBalanceKey() {
            Order buy = filledLimit(BUYER, Side.BUY, P_10_000, 5, P_9_000, 2);
            buy = buy.fill(new Quantity(3), P_10_000);

            Order sell1 = filledLimit(SELLER, Side.SELL, P_9_000, 2, P_9_000, 2);
            Order sell2 = filledLimit(SELLER2, Side.SELL, P_10_000, 3, P_10_000, 3);

            Trade trade1 = trade(buy, sell1, 2);
            Trade trade2 = trade(buy, sell2, 3);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buy, sell1, sell2), trade1, trade2));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 2_000L, -50_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 5L, 0L);
            assertDelta(deltas, SELLER, BTC_ASSET, 0L, -2L);
            assertDelta(deltas, SELLER, KRW, 18_000L, 0L);
            assertDelta(deltas, SELLER2, BTC_ASSET, 0L, -3L);
            assertDelta(deltas, SELLER2, KRW, 30_000L, 0L);
        }
    }

    @Nested
    @DisplayName("hold 반환")
    class HoldRefund {

        @Test
        @DisplayName("부분 체결 후 취소된 LIMIT BUY는 잔여 수량과 가격개선 차액을 함께 반환한다")
        void cancelledPartialLimitBuy_refundsRemainingHoldAndPriceImprovement() {
            Order buy = cancelledLimit(BUYER, Side.BUY, P_11_000, 5, P_10_000, 2);
            Order sell = filledLimit(SELLER, Side.SELL, P_10_000, 2, P_10_000, 2);
            Trade trade = trade(buy, sell, 2);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buy, sell), trade));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 35_000L, -55_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 2L, 0L);
            assertDelta(deltas, SELLER, BTC_ASSET, 0L, -2L);
            assertDelta(deltas, SELLER, KRW, 20_000L, 0L);
        }

        @Test
        @DisplayName("미체결 FOK 취소는 전체 hold를 available로 반환한다")
        void fokCancelledWithoutTrade_refundsEntireHold() {
            Order buy = cancelledLimit(BUYER, Side.BUY, P_10_000, 4);

            SettlementResult result = SettlementCalculator.settle(new SettlementInput(List.of(buy), List.of()));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 40_000L, -40_000L);
            assertThat(deltas).hasSize(1);
        }

        @Test
        @DisplayName("MARKET BUY는 미사용 quoteQty 예산을 반환한다")
        void marketBuy_refundsUnusedBudget() {
            Order buy = filledMarketBuy(BUYER, 35_000L, P_10_000, 3);
            Order sell = filledLimit(SELLER, Side.SELL, P_10_000, 3, P_10_000, 3);
            Trade trade = trade(buy, sell, 3);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buy, sell), trade));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, BUYER, KRW, 5_000L, -35_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 3L, 0L);
            assertDelta(deltas, SELLER, BTC_ASSET, 0L, -3L);
            assertDelta(deltas, SELLER, KRW, 30_000L, 0L);
        }

        @Test
        @DisplayName("MARKET SELL 부분 체결 후 취소는 잔여 base hold를 반환한다")
        void marketSell_partialFillThenCancel_refundsRemainingBase() {
            Order sell = cancelledMarketSell(SELLER, 5, P_10_000, 3);
            Order buyMaker = partiallyFilledLimit(BUYER, Side.BUY, P_10_000, 10, P_10_000, 3);
            Trade trade = trade(sell, buyMaker, 3);

            SettlementResult result = SettlementCalculator.settle(input(List.of(buyMaker, sell), trade));
            Map<BalanceKey, SettlementResult.BalanceDelta> deltas = toMap(result);

            assertDelta(deltas, SELLER, BTC_ASSET, 2L, -5L);
            assertDelta(deltas, SELLER, KRW, 30_000L, 0L);
            assertDelta(deltas, BUYER, KRW, 0L, -30_000L);
            assertDelta(deltas, BUYER, BTC_ASSET, 3L, 0L);
        }
    }

    @Test
    @DisplayName("trade에 포함된 order가 updatedOrders에 없으면 예외가 발생한다")
    void missingOrderForTrade_throwsBusinessRuleException() {
        Order buy = filledLimit(BUYER, Side.BUY, P_10_000, 1, P_10_000, 1);
        Order sell = filledLimit(SELLER, Side.SELL, P_10_000, 1, P_10_000, 1);
        Trade trade = trade(buy, sell, 1);

        assertThatThrownBy(() -> SettlementCalculator.settle(new SettlementInput(List.of(buy), List.of(trade))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("updatedOrders missing trade order");
    }

    private static SettlementInput input(List<Order> orders, Trade... trades) {
        return new SettlementInput(orders, List.of(trades));
    }

    private static Map<BalanceKey, SettlementResult.BalanceDelta> toMap(SettlementResult result) {
        return result.balanceDeltas().stream()
            .collect(java.util.stream.Collectors.toMap(
                delta -> new BalanceKey(delta.accountId(), delta.asset()),
                Function.identity()
            ));
    }

    private static void assertDelta(Map<BalanceKey, SettlementResult.BalanceDelta> deltas,
                                    AccountId accountId,
                                    Asset asset,
                                    long availableDelta,
                                    long heldDelta) {
        SettlementResult.BalanceDelta delta = deltas.get(new BalanceKey(accountId, asset));
        assertThat(delta).isNotNull();
        assertThat(delta.availableDelta()).isEqualTo(availableDelta);
        assertThat(delta.heldDelta()).isEqualTo(heldDelta);
    }

    private static Order activatedLimit(AccountId accountId, Side side, Price price, long qty) {
        return OrderFixture.createLimit(accountId, side, BTC, TimeInForce.GTC, price, new Quantity(qty)).activate();
    }

    private static Order filledLimit(AccountId accountId, Side side, Price orderPrice, long orderQty, Price executedPrice, long executedQty) {
        return activatedLimit(accountId, side, orderPrice, orderQty).fill(new Quantity(executedQty), executedPrice);
    }

    private static Order partiallyFilledLimit(AccountId accountId, Side side, Price orderPrice, long orderQty, Price executedPrice, long executedQty) {
        return filledLimit(accountId, side, orderPrice, orderQty, executedPrice, executedQty);
    }

    private static Order cancelledLimit(AccountId accountId, Side side, Price orderPrice, long orderQty) {
        return activatedLimit(accountId, side, orderPrice, orderQty).cancel();
    }

    private static Order cancelledLimit(AccountId accountId, Side side, Price orderPrice, long orderQty, Price executedPrice, long executedQty) {
        return activatedLimit(accountId, side, orderPrice, orderQty).fill(new Quantity(executedQty), executedPrice).cancel();
    }

    private static Order filledMarketBuy(AccountId accountId, long quoteQty, Price executedPrice, long executedQty) {
        return OrderFixture.createMarketBuyWithQuoteQty(accountId, Side.BUY, BTC, new QuoteQty(quoteQty))
                .activate()
                .fillQuoteMode(new Quantity(executedQty), executedPrice)
                .markFilledByMarketBuy();
    }

    private static Order cancelledMarketSell(AccountId accountId, long qty, Price executedPrice, long executedQty) {
        return OrderFixture.createMarketSell(accountId, BTC, new Quantity(qty))
                .activate()
                .fill(new Quantity(executedQty), executedPrice)
                .cancel();
    }

    private static Trade trade(Order taker, Order maker, long qty) {
        return Trade.of(taker, maker, new Quantity(qty));
    }

    private static AccountId accountId(String value) {
        return new AccountId(UUID.fromString(value));
    }

    private record BalanceKey(AccountId accountId, Asset asset) {}
}
