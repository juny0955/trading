package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.AccountsRecord;
import org.jooq.DSLContext;

final class JooqAccountMapper {

    static AccountsRecord toRecord(DSLContext dslContext, Account account) {
        AccountsRecord record = dslContext.newRecord(Tables.ACCOUNTS);
        record.setAccountId(account.getAccountId().value());
        record.setCreatedAt(account.getCreatedAt());
        return record;
    }
}
