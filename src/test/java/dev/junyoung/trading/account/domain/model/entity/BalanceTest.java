package dev.junyoung.trading.account.domain.model.entity;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
}
