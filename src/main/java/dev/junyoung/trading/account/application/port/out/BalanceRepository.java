package dev.junyoung.trading.account.application.port.out;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;

import java.util.Optional;

public interface BalanceRepository {
    Optional<Balance> findByAccountIdAndAsset(AccountId accountId, Asset asset);
}
