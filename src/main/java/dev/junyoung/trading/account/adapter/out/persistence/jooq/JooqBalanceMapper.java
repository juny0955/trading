package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import java.time.Instant;

import org.jooq.DSLContext;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;

final class JooqBalanceMapper {

    static BalancesRecord toRecord(DSLContext dslContext, AccountId accountId, Balance balance) {
        BalancesRecord record = dslContext.newRecord(Tables.BALANCES);
        record.setAccountId(accountId.value());
        record.setAsset(balance.getAsset().value());
        record.setAvailable(balance.getAvailable());
        record.setHeld(balance.getHeld());
        record.setCreatedAt(balance.getCreatedAt());
        record.setUpdatedAt(Instant.now());
        return record;
    }

    static Balance toDomain(BalancesRecord record) {
        return Balance.restore(
            new Asset(record.getAsset()),
            record.getAvailable(),
            record.getHeld(),
            record.getCreatedAt(),
            record.getUpdatedAt()
        );
    }
}
