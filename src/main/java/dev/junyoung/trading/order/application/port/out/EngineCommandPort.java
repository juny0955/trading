package dev.junyoung.trading.order.application.port.out;

import java.time.Instant;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.value.Symbol;

public interface EngineCommandPort {
	void submitPlace(Symbol symbol, Order order, Instant serviceEnteredAt);

	void submitCancel(Symbol symbol, long acceptedSeq, OrderId orderId, AccountId requesterAccountId, Instant serviceEnteredAt);
}
