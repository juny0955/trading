package dev.junyoung.trading.account.application.service;

import dev.junyoung.trading.account.application.exception.balance.BalanceNotFoundException;
import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.application.port.out.BalanceSettlementPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BalanceSettlementService implements BalanceSettlementPort {

    private final BalanceRepository balanceRepository;

    @Override
    public void balanceSettlement(AccountId accountId, Asset asset, long availableDelta, long heldDelta) {
        Balance balance = balanceRepository.findByAccountIdAndAssetForUpdate(accountId, asset)
            .orElseThrow(() -> new BalanceNotFoundException(accountId.toString(), asset.value()));

        Balance updated = balance.applyDelta(availableDelta, heldDelta);
        balanceRepository.save(accountId, updated);
    }
}
