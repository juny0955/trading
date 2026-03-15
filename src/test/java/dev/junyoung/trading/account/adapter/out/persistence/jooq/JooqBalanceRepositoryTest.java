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

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("없는 잔고를 save하면 BALANCES 1행이 삽입된다")
        void save_newBalance_insertsOneRow() {
            Balance balance = Balance.of(BTC, 200L, 50L);

            repository.save(ACCOUNT_ID, balance);

            int count = dslContext.fetchCount(
                    Tables.BALANCES,
                    Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq(BTC.value()))
            );
            assertThat(count).isEqualTo(1);

            var record = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value()))
                    .and(Tables.BALANCES.ASSET.eq(BTC.value()))
                    .fetchOne();
            assertThat(record).isNotNull();
            assertThat(record.getAvailable()).isEqualTo(200L);
            assertThat(record.getHeld()).isEqualTo(50L);
            assertThat(record.getCreatedAt()).isNotNull();
            assertThat(record.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 존재하는 (accountId, asset)에 save하면 행이 추가되지 않고 available·held·updatedAt만 갱신된다")
        void save_existingBalance_updatesWithoutAddingRow() {
            Instant now = Instant.now();
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, BTC.value())
                    .set(Tables.BALANCES.AVAILABLE, 100L)
                    .set(Tables.BALANCES.HELD, 0L)
                    .set(Tables.BALANCES.CREATED_AT, now)
                    .set(Tables.BALANCES.UPDATED_AT, now)
                    .execute();

            Balance updated = Balance.of(BTC, 70L, 30L);
            repository.save(ACCOUNT_ID, updated);

            int count = dslContext.fetchCount(
                    Tables.BALANCES,
                    Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value())
                            .and(Tables.BALANCES.ASSET.eq(BTC.value()))
            );
            assertThat(count).isEqualTo(1);

            var record = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value()))
                    .and(Tables.BALANCES.ASSET.eq(BTC.value()))
                    .fetchOne();
            assertThat(record).isNotNull();
            assertThat(record.getAvailable()).isEqualTo(70L);
            assertThat(record.getHeld()).isEqualTo(30L);
        }

        @Test
        @DisplayName("upsert 시 createdAt은 변경되지 않는다")
        void save_upsert_doesNotChangeCreatedAt() {
            Instant originalCreatedAt = Instant.parse("2024-01-01T00:00:00Z");
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, BTC.value())
                    .set(Tables.BALANCES.AVAILABLE, 100L)
                    .set(Tables.BALANCES.HELD, 0L)
                    .set(Tables.BALANCES.CREATED_AT, originalCreatedAt)
                    .set(Tables.BALANCES.UPDATED_AT, originalCreatedAt)
                    .execute();

            Balance updated = Balance.of(BTC, 50L, 50L);
            repository.save(ACCOUNT_ID, updated);

            var record = dslContext.selectFrom(Tables.BALANCES)
                    .where(Tables.BALANCES.ACCOUNT_ID.eq(ACCOUNT_ID.value()))
                    .and(Tables.BALANCES.ASSET.eq(BTC.value()))
                    .fetchOne();
            assertThat(record).isNotNull();
            assertThat(record.getCreatedAt()).isEqualTo(originalCreatedAt);
        }
    }

    @Nested
    @DisplayName("findByAccountIdAndAssetForUpdate()")
    class FindForUpdate {

        @Test
        @DisplayName("존재하는 잔고를 조회하면 findByAccountIdAndAsset()과 동일한 값을 반환한다")
        void findForUpdate_returnssamValueAsRegularFind() {
            Instant now = Instant.now();
            dslContext.insertInto(Tables.BALANCES)
                    .set(Tables.BALANCES.ACCOUNT_ID, ACCOUNT_ID.value())
                    .set(Tables.BALANCES.ASSET, BTC.value())
                    .set(Tables.BALANCES.AVAILABLE, 500L)
                    .set(Tables.BALANCES.HELD, 100L)
                    .set(Tables.BALANCES.CREATED_AT, now)
                    .set(Tables.BALANCES.UPDATED_AT, now)
                    .execute();

            Optional<Balance> regular = repository.findByAccountIdAndAsset(ACCOUNT_ID, BTC);
            Optional<Balance> forUpdate = repository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, BTC);

            assertThat(forUpdate).isPresent();
            assertThat(forUpdate.get().getAvailable()).isEqualTo(regular.get().getAvailable());
            assertThat(forUpdate.get().getHeld()).isEqualTo(regular.get().getHeld());
            assertThat(forUpdate.get().getAsset()).isEqualTo(regular.get().getAsset());
        }

        @Test
        @DisplayName("존재하지 않으면 Optional.empty()를 반환한다")
        void findForUpdate_returnsEmptyWhenNotExists() {
            Optional<Balance> result = repository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW);

            assertThat(result).isEmpty();
        }
    }
}
