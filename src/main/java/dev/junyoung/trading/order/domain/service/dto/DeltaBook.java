package dev.junyoung.trading.order.domain.service.dto;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public final class DeltaBook {
    private final Map<BalanceKey, Delta> deltas = new LinkedHashMap<>();

    public void subHeld(AccountId accountId, Asset asset, long execute) {
        BalanceKey balanceKey = new BalanceKey(accountId, asset);
        deltas.computeIfAbsent(balanceKey, _ -> new Delta())
            .subHeld(execute);
    }

    public void addAvailable(AccountId accountId, Asset asset, long execute) {
        BalanceKey balanceKey = new BalanceKey(accountId, asset);
        deltas.computeIfAbsent(balanceKey, _ -> new Delta())
            .addAvailable(execute);
    }

    public void refund(AccountId accountId, Asset asset, long refundAmount) {
        BalanceKey balanceKey = new BalanceKey(accountId, asset);
        Delta delta = deltas.computeIfAbsent(balanceKey, _ -> new Delta());
        delta.addAvailable(refundAmount);
        delta.subHeld(refundAmount);
    }

    public record BalanceKey(AccountId accountId, Asset asset) {}
}
