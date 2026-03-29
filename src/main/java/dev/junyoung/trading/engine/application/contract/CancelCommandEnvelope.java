package dev.junyoung.trading.engine.application.contract;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.value.Symbol;

public record CancelCommandEnvelope(
	Symbol symbol,
	long acceptedSeq,
	OrderId orderId,
	AccountId requesterAccountId
) { }
