package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.account.domain.model.value.AccountId;

public interface AccountQueryPort {
	boolean existsById(AccountId accountId);
}
