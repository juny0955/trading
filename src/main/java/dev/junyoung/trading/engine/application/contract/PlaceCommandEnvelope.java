package dev.junyoung.trading.engine.application.contract;

import java.time.Instant;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.shared.domain.enums.Side;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;
import dev.junyoung.trading.shared.domain.value.QuoteQty;
import dev.junyoung.trading.shared.domain.value.Symbol;

public record PlaceCommandEnvelope(
	OrderId orderId,
	AccountId accountId,
	String clientOrderId,
	long acceptedSeq,
	Side side,
	Symbol symbol,
	OrderType orderType,
	TimeInForce tif,
	Price price,
	QuoteQty quoteQty,
	Quantity quantity,
	Quantity remaining,
	OrderStatus status,
	QuoteQty cumQuoteQty,
	Quantity cumBaseQty,
	Instant orderedAt,
	Instant createdAt,
	Instant updatedAt
) { }
