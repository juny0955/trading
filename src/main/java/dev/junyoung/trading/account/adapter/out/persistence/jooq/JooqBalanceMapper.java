package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;
import org.jooq.DSLContext;

import java.time.Instant;
import java.util.UUID;

final class JooqBalanceMapper {

    static BalancesRecord toRecord(DSLContext dslContext, UUID accountId, Balance balance) {
        BalancesRecord record = dslContext.newRecord(Tables.BALANCES);
        record.setAccountId(accountId);
        record.setAsset(balance.getAsset().value());
        record.setAvailable(balance.getAvailable());
        record.setHeld(balance.getHeld());
        record.setCreatedAt(balance.getCreatedAt());
        record.setUpdatedAt(Instant.now());
        return record;
    }
}
