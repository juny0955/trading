package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;
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

    @Override
    public Optional<Balance> findByAccountIdAndAssetForUpdate(AccountId accountId, Asset asset) {
        return dslContext.selectFrom(Tables.BALANCES)
            .where(Tables.BALANCES.ACCOUNT_ID.eq(accountId.value()))
            .and(Tables.BALANCES.ASSET.eq(asset.value()))
            .forUpdate()
            .fetchOptional(JooqBalanceMapper::toDomain);
    }

    @Override
    public void save(AccountId accountId, Balance balance) {
        BalancesRecord record = JooqBalanceMapper.toRecord(dslContext, accountId, balance);
        dslContext.insertInto(Tables.BALANCES)
            .set(record)
            .onConflict(Tables.BALANCES.ACCOUNT_ID, Tables.BALANCES.ASSET)
            .doUpdate()
            .set(Tables.BALANCES.AVAILABLE, record.getAvailable())
            .set(Tables.BALANCES.HELD, record.getHeld())
            .set(Tables.BALANCES.UPDATED_AT, record.getUpdatedAt())
            .execute();
    }
}
