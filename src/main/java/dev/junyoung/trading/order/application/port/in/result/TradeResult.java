package dev.junyoung.trading.order.application.port.in.result;

import java.time.Instant;

import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;

public record TradeResult(
	String tradeId,
	String symbol,
	String orderId,
	String side,
	Long price,
	Long quantity,
	Instant tradedAt
) {
	public static TradeResult fromOrder(String orderId, Trade trade) {
		String buyOrderId = trade.buyOrderId().value().toString();
		String side = buyOrderId.equals(orderId) ? Side.BUY.name() : Side.SELL.name();

		return new TradeResult(
			trade.tradeId().value().toString(),
			trade.symbol().value(),
			orderId,
			side,
			trade.price().value(),
			trade.quantity().value(),
			trade.createdAt()
		);
	}

	public static TradeResult fromAccountResult(AccountTradeResult result) {
		return new TradeResult(
			result.tradeId().value().toString(),
			result.symbol().value(),
			result.orderId().value().toString(),
			result.side().name(),
			result.price().value(),
			result.quantity().value(),
			result.tradedAt()
		);
	}
}
