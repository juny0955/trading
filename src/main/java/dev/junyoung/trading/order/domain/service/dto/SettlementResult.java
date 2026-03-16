package dev.junyoung.trading.order.domain.service.dto;

import java.util.List;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;

public record SettlementResult(
    List<BalanceDelta> balanceDeltas
) {

    public SettlementResult {
        balanceDeltas = List.copyOf(balanceDeltas);
    }

    public static SettlementResult of(List<BalanceDelta> balanceDeltas) {
        return new SettlementResult(balanceDeltas);
    }

    public record BalanceDelta(
        AccountId accountId,
        Asset asset,
        long availableDelta,
        long heldDelta
    ) {
    }
}
