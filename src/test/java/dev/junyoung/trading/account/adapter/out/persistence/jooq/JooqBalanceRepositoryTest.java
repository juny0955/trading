package dev.junyoung.trading.account.adapter.out.persistence.jooq;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("JooqBalanceRepository")
class JooqBalanceRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqBalanceRepository repository;

    static final AccountId ACCOUNT_ID = new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    static final Asset BTC = new Asset("BTC");
    static final Asset KRW = new Asset("KRW");

    @BeforeEach
    void setUp() {
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
    }

    @Nested
    @DisplayName("findByAccountIdAndAsset()")
    class FindByAccountIdAndAsset {

        @Test
        @DisplayName("저장된 잔고를 조회하면 asset·available·held 필드가 일치한다")
        void returnsBalanceWhenExists() {
            Instant now = Instant.now();
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, BTC.value())
                    .set(Tables.BALANCES.AVAILABLE, 300L)
                    .set(Tables.BALANCES.HELD, 50L)
                    .set(Tables.BALANCES.CREATED_AT, now)
                    .set(Tables.BALANCES.UPDATED_AT, now)
                    .execute();

            Optional<Balance> result = repository.findByAccountIdAndAsset(ACCOUNT_ID, BTC);

            assertThat(result).isPresent();
            Balance balance = result.get();
            assertThat(balance.getAsset()).isEqualTo(BTC);
            assertThat(balance.getAvailable()).isEqualTo(300L);
            assertThat(balance.getHeld()).isEqualTo(50L);
        }

        @Test
        @DisplayName("존재하지 않는 Asset으로 조회하면 Optional.empty()를 반환한다")
        void returnsEmptyWhenAssetNotFound() {
            Optional<Balance> result = repository.findByAccountIdAndAsset(ACCOUNT_ID, KRW);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 AccountId로 조회하면 Optional.empty()를 반환한다")
        void returnsEmptyWhenAccountNotFound() {
            AccountId unknownId = new AccountId(UUID.randomUUID());

            Optional<Balance> result = repository.findByAccountIdAndAsset(unknownId, BTC);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("같은 Account의 다른 Asset은 서로 간섭하지 않는다")
        void returnsCorrectAssetAmongMultiple() {
            Instant now = Instant.now();
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, BTC.value())
                    .set(Tables.BALANCES.AVAILABLE, 100L)
                    .set(Tables.BALANCES.HELD, 0L)
                    .set(Tables.BALANCES.CREATED_AT, now)
                    .set(Tables.BALANCES.UPDATED_AT, now)
                    .execute();
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, KRW.value())
                    .set(Tables.BALANCES.AVAILABLE, 999_000L)
                    .set(Tables.BALANCES.HELD, 1_000L)
                    .set(Tables.BALANCES.CREATED_AT, now)
                    .set(Tables.BALANCES.UPDATED_AT, now)
                    .execute();

            Optional<Balance> result = repository.findByAccountIdAndAsset(ACCOUNT_ID, KRW);

            assertThat(result).isPresent();
            assertThat(result.get().getAvailable()).isEqualTo(999_000L);
            assertThat(result.get().getHeld()).isEqualTo(1_000L);
        }
    }
}
