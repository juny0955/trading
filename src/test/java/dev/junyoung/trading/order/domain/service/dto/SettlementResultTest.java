package dev.junyoung.trading.order.domain.service.dto;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettlementResult")
class SettlementResultTest {

    private static final AccountId ACCOUNT = new AccountId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

    @Test
    @DisplayName("balanceDeltas는 asset 이름 오름차순으로 정렬된다")
    void balanceDeltas_areSortedByAssetNameAscending() {
        DeltaBook deltaBook = new DeltaBook();
        deltaBook.addAvailable(ACCOUNT, new Asset("XRP"), 100L);
        deltaBook.addAvailable(ACCOUNT, new Asset("BTC"), 1L);
        deltaBook.addAvailable(ACCOUNT, new Asset("KRW"), 50_000L);
        deltaBook.addAvailable(ACCOUNT, new Asset("ETH"), 10L);

        SettlementResult result = SettlementResult.ofDeltaBook(deltaBook);

        List<String> assetNames = result.balanceDeltas().stream()
            .map(d -> d.asset().value())
            .toList();

        assertThat(assetNames).containsExactly("BTC", "ETH", "KRW", "XRP");
    }
}
