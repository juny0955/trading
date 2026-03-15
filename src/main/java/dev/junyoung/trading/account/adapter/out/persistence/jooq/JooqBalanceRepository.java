package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JooqBalanceRepository implements BalanceRepository {

    private final DSLContext dslContext;

    @Override
    public Optional<Balance> findByAccountIdAndAsset(AccountId accountId, Asset asset) {
        return dslContext.selectFrom(Tables.BALANCES)
            .where(Tables.BALANCES.ACCOUNT_ID.eq(accountId.value()))
            .and(Tables.BALANCES.ASSET.eq(asset.value()))
            .fetchOptional(JooqBalanceMapper::toDomain);
    }
}
