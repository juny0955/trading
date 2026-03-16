package dev.junyoung.trading.account.application.service;

import dev.junyoung.trading.account.application.exception.balance.BalanceNotFountException;
import org.springframework.stereotype.Service;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.application.port.out.HoldReservationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class HoldReservationService implements HoldReservationPort {

	private final BalanceRepository balanceRepository;

	@Override
	public void reserve(AccountId accountId, Asset asset, long amount) {
		Balance balance = balanceRepository.findByAccountIdAndAssetForUpdate(accountId, asset)
			.orElse(Balance.zeroOf(asset));

		Balance reserve = balance.reserve(amount);
		balanceRepository.save(accountId, reserve);
	}

	@Override
	public void release(AccountId accountId, Asset asset, long amount) {
		Balance balance = balanceRepository.findByAccountIdAndAssetForUpdate(accountId, asset)
			.orElseThrow(() -> new BalanceNotFountException(accountId.toString(), asset.value()));

		Balance release = balance.release(amount);
		balanceRepository.save(accountId, release);
	}
}
