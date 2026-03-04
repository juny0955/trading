package dev.junyoung.trading.order.application.port.in.command;

import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public record PlaceOrderCommand(
	Symbol symbol,
	Side side,
	OrderType orderType,
	TimeInForce tif,
	Price price,
	Quantity quantity
) {
}
