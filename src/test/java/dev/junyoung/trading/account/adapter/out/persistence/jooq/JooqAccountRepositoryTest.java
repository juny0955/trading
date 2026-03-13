package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.domain.model.entity.Account;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.BalancesRecord;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("JooqAccountRepository")
class JooqAccountRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqAccountRepository repository;

    static final AccountId ACCOUNT_ID = new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    static final Asset BTC = new Asset("BTC");
    static final Asset KRW = new Asset("KRW");

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("잔고 없는 Account를 저장하면 ACCOUNTS 1행, BALANCES 0행이다")
        void savesAccountWithNoBalances() {
            Account account = new Account(ACCOUNT_ID, null, Instant.now());

            repository.save(account);

            int accountCount = dslContext.fetchCount(Tables.ACCOUNTS, Tables.ACCOUNTS.ACCOUNT_ID.eq(ACCOUNT_ID.value()));
            int balanceCount = dslContext.fetchCount(Tables.BALANCES, Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value()));

            assertThat(accountCount).isEqualTo(1);
            assertThat(balanceCount).isEqualTo(0);
        }

        @Test
        @DisplayName("BTC·KRW 잔고 포함 Account를 저장하면 BALANCES 2행이 저장된다")
        void savesAccountWithBalances() {
            Balance btc = Balance.of(BTC, 100L, 10L);
            Balance krw = Balance.of(KRW, 500_000L, 0L);
            Account account = new Account(ACCOUNT_ID, Map.of(BTC, btc, KRW, krw), Instant.now());

            repository.save(account);

            int balanceCount = dslContext.fetchCount(Tables.BALANCES, Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value()));
            assertThat(balanceCount).isEqualTo(2);

            BalancesRecord btcRow = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq("BTC")))
                    .fetchOne();
            assertThat(btcRow).isNotNull();
            assertThat(btcRow.getAvailable()).isEqualTo(100L);
            assertThat(btcRow.getHeld()).isEqualTo(10L);

            BalancesRecord krwRow = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq("KRW")))
                    .fetchOne();
            assertThat(krwRow).isNotNull();
            assertThat(krwRow.getAvailable()).isEqualTo(500_000L);
            assertThat(krwRow.getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("동일 Account를 다른 잔고로 두 번 저장하면 ACCOUNTS 1행, BALANCES 갱신, created_at 불변")
        void upsertUpdatesBalanceOnConflict() {
            Balance btcFirst = Balance.of(BTC, 100L, 0L);
            Account first = new Account(ACCOUNT_ID, Map.of(BTC, btcFirst), Instant.now());
            repository.save(first);

            Instant createdAtBefore = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq("BTC")))
                    .fetchOne(Tables.BALANCES.CREATED_AT);

            long newQty = 999L;
            Balance btcSecond = Balance.of(BTC, newQty, 5L);
            Account second = new Account(ACCOUNT_ID, Map.of(BTC, btcSecond), Instant.now());
            repository.save(second);

            int accountCount = dslContext.fetchCount(Tables.ACCOUNTS, Tables.ACCOUNTS.ACCOUNT_ID.eq(ACCOUNT_ID.value()));
            assertThat(accountCount).isEqualTo(1);

            BalancesRecord row = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq("BTC")))
                    .fetchOne();
            assertThat(row).isNotNull();
            assertThat(row.getAvailable()).isEqualTo(newQty);
            assertThat(row.getHeld()).isEqualTo(5L);
            assertThat(row.getCreatedAt()).isEqualTo(createdAtBefore);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("저장 후 조회하면 AccountId·createdAt·balances 맵이 일치한다")
        void returnsAccountWithBalances() {
            Instant now = Instant.now();
            Balance btc = Balance.of(BTC, 200L, 20L);
            Account account = new Account(ACCOUNT_ID, Map.of(BTC, btc), now);
            repository.save(account);

            Optional<Account> result = repository.findById(ACCOUNT_ID);

            assertThat(result).isPresent();
            Account found = result.get();
            assertThat(found.getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(found.getBalances()).containsKey(BTC);
            assertThat(found.getBalances().get(BTC).getAvailable()).isEqualTo(200L);
            assertThat(found.getBalances().get(BTC).getHeld()).isEqualTo(20L);
        }

        @Test
        @DisplayName("잔고 없이 저장 후 조회하면 balances 맵이 비어있다")
        void returnsAccountWithEmptyBalances() {
            Account account = new Account(ACCOUNT_ID, null, Instant.now());
            repository.save(account);

            Optional<Account> result = repository.findById(ACCOUNT_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getBalances()).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 AccountId로 조회하면 Optional.empty()를 반환한다")
        void returnsEmptyWhenNotFound() {
            AccountId unknownId = new AccountId(UUID.randomUUID());

            Optional<Account> result = repository.findById(unknownId);

            assertThat(result).isEmpty();
        }
    }
}
