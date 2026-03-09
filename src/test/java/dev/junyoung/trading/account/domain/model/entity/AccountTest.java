package dev.junyoung.trading.account.domain.model.entity;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Account")
class AccountTest {

    @Test
    @DisplayName("balances가 null이면 빈 맵으로 생성된다")
    void createAccountWithEmptyBalancesWhenNull() {
        Account account = new Account(AccountId.newId(), null, Instant.now());

        assertThat(account.getBalances()).isEmpty();
    }

    @Test
    @DisplayName("balances는 불변 복사본으로 보관된다")
    void storesImmutableCopyOfBalances() {
        Asset asset = new Asset("KRW");
        Map<Asset, Balance> balances = new HashMap<>();
        balances.put(asset, Balance.of(asset, 100_000L, 0L));

        Account account = new Account(AccountId.newId(), balances, Instant.now());
        balances.clear();

        assertThat(account.getBalances()).hasSize(1);
        assertThat(account.getBalances().get(asset).getAvailable()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("balances는 외부에서 수정할 수 없다")
    void balancesAreUnmodifiable() {
        Asset asset = new Asset("KRW");
        Account account = new Account(
            AccountId.newId(),
            Map.of(asset, Balance.of(asset, 100_000L, 0L)),
            Instant.now()
        );

        assertThrows(UnsupportedOperationException.class, () -> account.getBalances().put(asset, Balance.of(asset, 0L, 0L)));
    }
}
