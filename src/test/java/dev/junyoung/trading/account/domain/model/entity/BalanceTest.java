package dev.junyoung.trading.account.domain.model.entity;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Balance")
class BalanceTest {

    private static final Asset ASSET = new Asset("KRW");

    @Test
    @DisplayName("available과 held가 모두 0 이상이면 생성된다")
    void createBalance() {
        Balance balance = Balance.of(ASSET, 100_000L, 20_000L);

        assertThat(balance.getAsset()).isEqualTo(ASSET);
        assertThat(balance.getAvailable()).isEqualTo(100_000L);
        assertThat(balance.getHeld()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("total은 available + held를 반환한다")
    void totalReturnsAvailablePlusHeld() {
        Balance balance = Balance.of(ASSET, 100_000L, 20_000L);

        assertThat(balance.total()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("available이 음수면 생성에 실패한다")
    void rejectNegativeAvailable() {
        assertThrows(BusinessRuleException.class, () -> Balance.of(ASSET, -1L, 0L));
    }

    @Test
    @DisplayName("held가 음수면 생성에 실패한다")
    void rejectNegativeHeld() {
        assertThrows(BusinessRuleException.class, () -> Balance.of(ASSET, 0L, -1L));
    }

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("available >= amount이면 available이 줄고 held가 늘어난 새 Balance를 반환한다")
        void reserve_decreasesAvailableAndIncreasesHeld() {
            Balance balance = Balance.of(ASSET, 100_000L, 0L);

            Balance reserved = balance.reserve(30_000L);

            assertThat(reserved.getAvailable()).isEqualTo(70_000L);
            assertThat(reserved.getHeld()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("amount == available이면 available=0, held=amount인 Balance를 반환한다")
        void reserve_exactAmount_returnsZeroAvailableAndFullHeld() {
            Balance balance = Balance.of(ASSET, 50_000L, 0L);

            Balance reserved = balance.reserve(50_000L);

            assertThat(reserved.getAvailable()).isEqualTo(0L);
            assertThat(reserved.getHeld()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("amount > available이면 BusinessRuleException(BALANCE_INSUFFICIENT)을 던진다")
        void reserve_insufficientAvailable_throwsException() {
            Balance balance = Balance.of(ASSET, 10_000L, 0L);

            assertThatThrownBy(() -> balance.reserve(20_000L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("reserve()는 원본 Balance를 변경하지 않는다 (불변성)")
        void reserve_doesNotMutateOriginal() {
            Balance original = Balance.of(ASSET, 100_000L, 5_000L);

            original.reserve(30_000L);

            assertThat(original.getAvailable()).isEqualTo(100_000L);
            assertThat(original.getHeld()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("reserve() 후 updatedAt이 createdAt보다 같거나 늦다")
        void reserve_updatedAtIsNotBeforeCreatedAt() {
            Balance balance = Balance.of(ASSET, 100_000L, 0L);

            Balance reserved = balance.reserve(10_000L);

            assertThat(reserved.getUpdatedAt()).isAfterOrEqualTo(reserved.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("zeroOf()")
    class ZeroOf {

        @Test
        @DisplayName("zeroOf()로 생성된 Balance는 available=0, held=0이다")
        void zeroOf_createsBalanceWithZeroValues() {
            Balance balance = Balance.zeroOf(ASSET);

            assertThat(balance.getAvailable()).isEqualTo(0L);
            assertThat(balance.getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("total()은 0이다")
        void zeroOf_totalIsZero() {
            Balance balance = Balance.zeroOf(ASSET);

            assertThat(balance.total()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("restore()는 전달된 createdAt·updatedAt을 그대로 보존한다")
        void restore_preservesTimestamps() {
            Instant createdAt = Instant.parse("2024-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2024-06-01T12:00:00Z");

            Balance balance = Balance.restore(ASSET, 500L, 100L, createdAt, updatedAt);

            assertThat(balance.getCreatedAt()).isEqualTo(createdAt);
            assertThat(balance.getUpdatedAt()).isEqualTo(updatedAt);
        }
    }
}
