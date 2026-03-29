package dev.junyoung.trading.order.application.port.out.result;

import java.time.Instant;

import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.Symbol;
import dev.junyoung.trading.order.domain.model.value.TradeId;

public record AccountTradeResult(
	TradeId tradeId,
	Symbol symbol,
	OrderId orderId,
	Side side,
	Price price,
	Quantity quantity,
	Instant tradedAt
) {
}
