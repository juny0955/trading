package dev.junyoung.trading.account.domain.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;

@DisplayName("BalanceHoldPolicy")
class BalanceHoldPolicyTest {

    private static final Asset SETTLEMENT_ASSET = new Asset("KRW");
    private static final Symbol SYMBOL = new Symbol("BTC");

    @Test
    @DisplayName("LIMIT BUY는 정산 자산을 price * quantity만큼 hold한다")
    void holdLimitBuyInSettlementAsset() {
        Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(3));

        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);

        assertThat(holdSpec.asset()).isEqualTo(SETTLEMENT_ASSET);
        assertThat(holdSpec.amount()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("LIMIT SELL은 주문 심볼 자산을 quantity만큼 hold한다")
    void holdLimitSellInSymbolAsset() {
        Order order = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, new Price(10_000), new Quantity(3));

        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);

        assertThat(holdSpec.asset()).isEqualTo(new Asset("BTC"));
        assertThat(holdSpec.amount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("MARKET BUY quoteQty는 정산 자산을 quoteQty만큼 hold한다")
    void holdMarketBuyQuoteQtyInSettlementAsset() {
        Order order = OrderFixture.createMarketBuyWithQuoteQty(Side.BUY, SYMBOL, new QuoteQty(50_000));

        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);

        assertThat(holdSpec.asset()).isEqualTo(SETTLEMENT_ASSET);
        assertThat(holdSpec.amount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("MARKET SELL은 주문 심볼 자산을 quantity만큼 hold한다")
    void holdMarketSellInSymbolAsset() {
        Order order = OrderFixture.createMarketSell(SYMBOL, new Quantity(2));

        BalanceHoldPolicy.HoldSpec holdSpec = BalanceHoldPolicy.holdSpecFor(order);

        assertThat(holdSpec.asset()).isEqualTo(new Asset("BTC"));
        assertThat(holdSpec.amount()).isEqualTo(2L);
    }

}
