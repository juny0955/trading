package dev.junyoung.trading.order.domain.service.dto;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.service.state.OrderBookView;

public record CancelCalculationInput(
	OrderBookView view,
	Symbol symbol,
	OrderId orderId,
	AccountId requestingAccountId,
	Order target
) {
}
