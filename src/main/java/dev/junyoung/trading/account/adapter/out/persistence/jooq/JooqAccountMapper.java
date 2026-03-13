package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.AccountsRecord;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;
import org.jooq.DSLContext;
import org.jooq.Result;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class JooqAccountMapper {

    static AccountsRecord toRecord(DSLContext dslContext, Account account) {
        AccountsRecord record = dslContext.newRecord(Tables.ACCOUNTS);
        record.setAccountId(account.getAccountId().value());
        record.setCreatedAt(account.getCreatedAt());
        return record;
    }

    static Account toDomain(AccountsRecord accountsRecord, Result<BalancesRecord> balancesRecords) {
        Map<Asset, Balance> balances = balancesRecords.stream()
            .map(r ->
                Balance.restore(
                    new Asset(r.getAsset()),
                    r.getAvailable(),
                    r.getHeld(),
                    r.getCreatedAt(),
                    r.getUpdatedAt()
                )
            ).collect(Collectors.toMap(
                Balance::getAsset,
                Function.identity()
            ));


        return Account.restore(
            new AccountId(accountsRecord.getAccountId()),
            balances,
            accountsRecord.getCreatedAt()
        );
    }
}
