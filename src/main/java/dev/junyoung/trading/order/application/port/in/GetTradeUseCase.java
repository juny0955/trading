package dev.junyoung.trading.order.application.port.in;

import java.util.List;

import dev.junyoung.trading.order.application.port.in.result.TradeResult;

public interface GetTradeUseCase {
	List<TradeResult> getTradesByOrder(String accountId, String orderId);
	List<TradeResult> getTradesByAccount(String accountId);
}
