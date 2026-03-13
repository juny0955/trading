package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.application.port.out.AccountRepository;
import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JooqAccountRepository implements AccountRepository {

    private final DSLContext dslContext;

    @Override
    public void save(Account account) {
        dslContext.insertInto(Tables.ACCOUNTS)
            .set(JooqAccountMapper.toRecord(dslContext, account))
            .onConflict(Tables.ACCOUNTS.ACCOUNT_ID)
            .doNothing()
            .execute();

        List<BalancesRecord> balancesRecords = account.getBalances().values().stream()
            .map(b -> JooqBalanceMapper.toRecord(
                dslContext,
                account.getAccountId().value(),
                b
            ))
            .toList();

        for (BalancesRecord balancesRecord : balancesRecords) {
            dslContext.insertInto(Tables.BALANCES)
                .set(balancesRecord)
                .onConflict(Tables.BALANCES.ACCOUNT_ID, Tables.BALANCES.ASSET)
                .doUpdate()
                .set(Tables.BALANCES.AVAILABLE, DSL.excluded(Tables.BALANCES.AVAILABLE))
                .set(Tables.BALANCES.HELD, DSL.excluded(Tables.BALANCES.HELD))
                .set(Tables.BALANCES.UPDATED_AT, DSL.excluded(Tables.BALANCES.UPDATED_AT))
                .execute();
        }
    }
}
