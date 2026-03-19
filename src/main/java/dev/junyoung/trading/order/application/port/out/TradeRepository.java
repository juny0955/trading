package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.value.OrderId;

import java.util.List;

public interface TradeRepository {
    void saveAll(List<Trade> trades);

	List<Trade> findByOrderId(OrderId orderId);
	List<AccountTradeResult> findByAccountIdWithSide(AccountId accountId);
}
