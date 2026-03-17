package dev.junyoung.trading.account.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import dev.junyoung.trading.account.application.exception.balance.BalanceNotFoundException;
import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceSettlementService")
class BalanceSettlementServiceTest {

    private static final AccountId ACCOUNT_ID =
            new AccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final Asset KRW = new Asset("KRW");
    private static final Asset BTC = new Asset("BTC");

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private BalanceSettlementService sut;

    @Nested
    @DisplayName("balanceSettlement()")
    class BalanceSettlement {

        @Test
        @DisplayName("availableDelta 양수 적용 — available 증가")
        void applyPositiveAvailableDelta() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, KRW, 10_000L, 0L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(60_000L);
            assertThat(saved.getHeld()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("heldDelta 음수 적용 — held 감소")
        void applyNegativeHeldDelta() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, KRW, 0L, -20_000L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(50_000L);
            assertThat(saved.getHeld()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("거래 정산 delta — held 전액 차감")
        void applyTradeSettlementDelta() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, KRW, 0L, -30_000L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(50_000L);
            assertThat(saved.getHeld()).isEqualTo(0L);
        }

        @Test
        @DisplayName("환불 delta 적용 — available 증가, held 감소")
        void applyRefundDelta() {
            Balance existing = Balance.of(BTC, 0L, 5L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, BTC))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, BTC, 3L, -3L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(3L);
            assertThat(saved.getHeld()).isEqualTo(2L);
        }

        @Test
        @DisplayName("save에는 올바른 accountId가 전달된다")
        void saveCalledWithCorrectAccountId() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, KRW, 10_000L, 0L);

            ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
            verify(balanceRepository).save(accountIdCaptor.capture(), any(Balance.class));
            assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
        }

        @Test
        @DisplayName("findByAccountIdAndAssetForUpdate()를 사용한다 — 일반 조회 미사용")
        void usesFindForUpdate_notRegularFind() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.balanceSettlement(ACCOUNT_ID, KRW, 10_000L, 0L);

            verify(balanceRepository).findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW);
            verify(balanceRepository, never()).findByAccountIdAndAsset(any(), any());
        }

        @Test
        @DisplayName("잔고가 없으면 BalanceNotFoundException이 발생한다")
        void throwsBalanceNotFoundException_whenBalanceNotFound() {
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.balanceSettlement(ACCOUNT_ID, KRW, 0L, -20_000L))
                    .isInstanceOf(BalanceNotFoundException.class);
        }

        @Test
        @DisplayName("delta 적용 후 available이 음수가 되면 BusinessRuleException이 발생한다")
        void throwsBusinessRuleException_whenAvailableBecomesNegative() {
            Balance existing = Balance.of(KRW, 1_000L, 5_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> sut.balanceSettlement(ACCOUNT_ID, KRW, -2_000L, 0L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("delta 적용 후 held가 음수가 되면 BusinessRuleException이 발생한다")
        void throwsBusinessRuleException_whenHeldBecomesNegative() {
            Balance existing = Balance.of(KRW, 5_000L, 1_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> sut.balanceSettlement(ACCOUNT_ID, KRW, 0L, -2_000L))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}
