package dev.junyoung.trading.order.fixture;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public class OrderFixture {

    private OrderFixture() {
    }

    /** LIMIT 주문 생성 (tif 지정) */
    public static Order createLimit(Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return Order.create(symbol, side, OrderType.LIMIT, tif, price, null, quantity);
    }

    /** MARKET SELL 주문 생성 (quantity 기반) */
    public static Order createMarketSell(Symbol symbol, Quantity quantity) {
        return Order.create(symbol, Side.SELL, OrderType.MARKET, null, null, null, quantity);
    }

    /** quoteQty 기반 MARKET BUY 주문 생성 */
    public static Order createMarketBuyWithQuoteQty(Side side, Symbol symbol, QuoteQty quoteQty) {
        return Order.create(symbol, side, OrderType.MARKET, null, null, quoteQty, null);
    }
}
