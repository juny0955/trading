package dev.junyoung.trading.account.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import dev.junyoung.trading.account.application.exception.balance.BalanceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;

@ExtendWith(MockitoExtension.class)
@DisplayName("HoldReservationService")
class HoldReservationServiceTest {

    private static final AccountId ACCOUNT_ID =
            new AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final Asset KRW = new Asset("KRW");

    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private HoldReservationService sut;

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("잔고가 존재하면 findByAccountIdAndAssetForUpdate()로 조회 후 reserve()된 잔고를 save한다")
        void reserve_existingBalance_savesReservedBalance() {
            Balance existing = Balance.of(KRW, 100_000L, 0L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.reserve(ACCOUNT_ID, KRW, 30_000L);

            verify(balanceRepository).save(eq(ACCOUNT_ID), any(Balance.class));
        }

        @Test
        @DisplayName("잔고가 없으면 Balance.zeroOf()로 생성한 뒤 available이 충분할 때 reserve()된 잔고를 save한다")
        void reserve_noBalance_whenAmountIsZero_throwsException() {
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.empty());

            // zeroOf(KRW) → available=0, reserve(50_000) → BALANCE_INSUFFICIENT
            assertThatThrownBy(() -> sut.reserve(ACCOUNT_ID, KRW, 50_000L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("reserve() 결과(새 Balance)가 save에 전달된다 — available 감소, held 증가")
        void reserve_savedBalanceReflectsReservation() {
            Balance existing = Balance.of(KRW, 100_000L, 5_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.reserve(ACCOUNT_ID, KRW, 20_000L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(80_000L);
            assertThat(saved.getHeld()).isEqualTo(25_000L);
        }

        @Test
        @DisplayName("잔고가 부족하면 BusinessRuleException이 전파된다")
        void reserve_insufficientBalance_throwsException() {
            Balance existing = Balance.of(KRW, 1_000L, 0L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> sut.reserve(ACCOUNT_ID, KRW, 50_000L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("findByAccountIdAndAsset()이 아닌 findByAccountIdAndAssetForUpdate()를 사용한다 (pessimistic lock 확인)")
        void reserve_usesFindForUpdate_notRegularFind() {
            Balance existing = Balance.of(KRW, 100_000L, 0L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.reserve(ACCOUNT_ID, KRW, 10_000L);

            verify(balanceRepository).findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW);
            verify(balanceRepository, never()).findByAccountIdAndAsset(any(), any());
        }

        @Test
        @DisplayName("save에는 올바른 accountId가 전달된다")
        void reserve_saveCalledWithCorrectAccountId() {
            Balance existing = Balance.of(KRW, 100_000L, 0L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.reserve(ACCOUNT_ID, KRW, 10_000L);

            ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
            verify(balanceRepository).save(accountIdCaptor.capture(), any(Balance.class));
            assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
        }
    }

    @Nested
    @DisplayName("release()")
    class Release {

        @Test
        @DisplayName("잔고가 존재하면 findByAccountIdAndAssetForUpdate()로 조회 후 release()된 잔고를 save한다")
        void release_existingBalance_savesReleasedBalance() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.release(ACCOUNT_ID, KRW, 10_000L);

            verify(balanceRepository).save(eq(ACCOUNT_ID), any(Balance.class));
        }

        @Test
        @DisplayName("잔고가 없으면 BalanceNotFountException이 전파된다")
        void release_noBalance_throwsBalanceNotFoundException() {
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.release(ACCOUNT_ID, KRW, 10_000L))
                    .isInstanceOf(BalanceNotFoundException.class);
        }

        @Test
        @DisplayName("release() 결과가 save에 전달된다 — available 증가, held 감소")
        void release_savedBalanceReflectsRelease() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.release(ACCOUNT_ID, KRW, 20_000L);

            ArgumentCaptor<Balance> captor = ArgumentCaptor.forClass(Balance.class);
            verify(balanceRepository).save(eq(ACCOUNT_ID), captor.capture());
            Balance saved = captor.getValue();
            assertThat(saved.getAvailable()).isEqualTo(70_000L);
            assertThat(saved.getHeld()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("held 잔고 부족 시 BusinessRuleException이 전파된다")
        void release_insufficientHeld_throwsException() {
            Balance existing = Balance.of(KRW, 50_000L, 5_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> sut.release(ACCOUNT_ID, KRW, 10_000L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("findByAccountIdAndAsset()이 아닌 findByAccountIdAndAssetForUpdate()를 사용한다 (pessimistic lock 확인)")
        void release_usesFindForUpdate_notRegularFind() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.release(ACCOUNT_ID, KRW, 10_000L);

            verify(balanceRepository).findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW);
            verify(balanceRepository, never()).findByAccountIdAndAsset(any(), any());
        }

        @Test
        @DisplayName("save에는 올바른 accountId가 전달된다")
        void release_saveCalledWithCorrectAccountId() {
            Balance existing = Balance.of(KRW, 50_000L, 30_000L);
            when(balanceRepository.findByAccountIdAndAssetForUpdate(ACCOUNT_ID, KRW))
                    .thenReturn(Optional.of(existing));

            sut.release(ACCOUNT_ID, KRW, 10_000L);

            ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
            verify(balanceRepository).save(accountIdCaptor.capture(), any(Balance.class));
            assertThat(accountIdCaptor.getValue()).isEqualTo(ACCOUNT_ID);
        }
    }
}
