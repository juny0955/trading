package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;

public interface BalanceSettlementPort {
    void balanceSettlement(AccountId accountId, Asset asset, long availableDelta, long heldDelta);
}
