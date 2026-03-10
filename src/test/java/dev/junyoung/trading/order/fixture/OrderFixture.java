package dev.junyoung.trading.order.fixture;

import java.util.UUID;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public class OrderFixture {

    public static final AccountId DEFAULT_ACCOUNT_ID =
            new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private OrderFixture() {
    }

    /** LIMIT 주문 생성 (tif 지정) */
    public static Order createLimit(Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return createLimit(DEFAULT_ACCOUNT_ID, side, symbol, tif, price, quantity);
    }

    public static Order createLimit(AccountId accountId, Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return Order.create(accountId, symbol, side, OrderType.LIMIT, tif, price, null, quantity);
    }

    /** MARKET SELL 주문 생성 (quantity 기반) */
    public static Order createMarketSell(Symbol symbol, Quantity quantity) {
        return createMarketSell(DEFAULT_ACCOUNT_ID, symbol, quantity);
    }

    public static Order createMarketSell(AccountId accountId, Symbol symbol, Quantity quantity) {
        return Order.create(accountId, symbol, Side.SELL, OrderType.MARKET, null, null, null, quantity);
    }

    /** quoteQty 기반 MARKET BUY 주문 생성 */
    public static Order createMarketBuyWithQuoteQty(Side side, Symbol symbol, QuoteQty quoteQty) {
        return createMarketBuyWithQuoteQty(DEFAULT_ACCOUNT_ID, side, symbol, quoteQty);
    }

    public static Order createMarketBuyWithQuoteQty(AccountId accountId, Side side, Symbol symbol, QuoteQty quoteQty) {
        return Order.create(accountId, symbol, side, OrderType.MARKET, null, null, quoteQty, null);
    }
}
