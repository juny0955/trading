package dev.junyoung.trading.order.application.port.in.command;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.QuoteQty;
import dev.junyoung.trading.shared.domain.value.Symbol;

public record PlaceOrderCommand(
	AccountId accountId,
	Symbol symbol,
	Side side,
	OrderType orderType,
	TimeInForce tif,
	Price price,
	QuoteQty quoteQty,
	Quantity quantity,
	String clientOrderId
) {
}
