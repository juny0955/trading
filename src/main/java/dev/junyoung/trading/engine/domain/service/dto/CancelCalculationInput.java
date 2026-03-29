package dev.junyoung.trading.engine.domain.service.dto;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.engine.domain.service.state.OrderBookView;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public record CancelCalculationInput(
	OrderBookView view,
	Symbol symbol,
	long commandSeq,
	OrderId orderId,
	AccountId requestingAccountId,
	Order target
) {
}
