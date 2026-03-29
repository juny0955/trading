package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.domain.model.value.OrderId;

public interface IdempotencyKeyRepository {
	void save(AccountId accountId, OrderId orderId, String clientOrderId);
	OrderId findOrderId(AccountId accountId, String clientOrderId);

    void delete(AccountId accountId, String clientOrderId);
}
