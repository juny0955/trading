package dev.junyoung.trading.account.application.port.out;

import java.util.Optional;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;

public interface BalanceRepository {
    Optional<Balance> findByAccountIdAndAsset(AccountId accountId, Asset asset);
    Optional<Balance> findByAccountIdAndAssetForUpdate(AccountId accountId, Asset asset);
    void save(AccountId accountId, Balance balance);
}
