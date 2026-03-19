package dev.junyoung.trading.order.adapter.in.rest.response;

import java.time.Instant;

import dev.junyoung.trading.order.application.port.in.result.TradeResult;

public record TradeResponse(
	String tradeId,
	String symbol,
	String orderId,
	String side,
	Long price,
	Long quantity,
	Instant tradedAt
) {
	public static TradeResponse from(TradeResult result) {
		return new TradeResponse (
			result.tradeId(),
			result.symbol(),
			result.orderId(),
			result.side(),
			result.price(),
			result.quantity(),
			result.tradedAt()
		);
	}
}
