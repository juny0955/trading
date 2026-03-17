package dev.junyoung.trading.order.domain.service.dto;

import java.util.Comparator;
import java.util.List;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;

public record SettlementResult(
    List<BalanceDelta> balanceDeltas
) {
    public static SettlementResult ofDeltaBook(DeltaBook deltaBook) {
        return new SettlementResult(
            deltaBook.getDeltas().entrySet().stream()
                .filter(entry -> !entry.getValue().isZero())
                .map(entry -> new BalanceDelta(
                    entry.getKey().accountId(),
                    entry.getKey().asset(),
                    entry.getValue().getAvailableDelta(),
                    entry.getValue().getHeldDelta()
                ))
                .sorted(Comparator
                    .comparing((BalanceDelta d) -> d.accountId.value())
                    .thenComparing(d -> d.asset.value())
                )
                .toList()
        );
    }

    public record BalanceDelta(
        AccountId accountId,
        Asset asset,
        long availableDelta,
        long heldDelta
    ) { }
}
