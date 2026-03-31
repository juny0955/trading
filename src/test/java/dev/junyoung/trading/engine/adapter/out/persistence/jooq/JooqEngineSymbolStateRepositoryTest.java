package dev.junyoung.trading.engine.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.engine.domain.entity.EngineSymbolState;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.shared.domain.value.Symbol;

@SpringBootTest
@Transactional
@DisplayName("JooqEngineSymbolStateRepository")
class JooqEngineSymbolStateRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqEngineSymbolStateRepository repository;

    // 기존 seed(BTC, ETH, TEST)와 충돌하지 않는 테스트 전용 심볼 prefix
    private static final String SYM_A = "BTCTEST_A";
    private static final String SYM_B = "BTCTEST_B";
    private static final String SYM_C = "BTCTEST_C";

    @BeforeEach
    void setUp() {
        // BTC, KRW는 seed에 이미 있으므로 충돌 무시
        dslContext.insertInto(Tables.ASSETS)
                .set(Tables.ASSETS.ASSET_CODE, "BTC").set(Tables.ASSETS.STATUS, "ACTIVE")
                .set(Tables.ASSETS.CREATED_AT, Instant.now()).set(Tables.ASSETS.UPDATED_AT, Instant.now())
                .onConflict(Tables.ASSETS.ASSET_CODE).doNothing().execute();
        dslContext.insertInto(Tables.ASSETS)
                .set(Tables.ASSETS.ASSET_CODE, "KRW").set(Tables.ASSETS.STATUS, "ACTIVE")
                .set(Tables.ASSETS.CREATED_AT, Instant.now()).set(Tables.ASSETS.UPDATED_AT, Instant.now())
                .onConflict(Tables.ASSETS.ASSET_CODE).doNothing().execute();
    }

    private void insertSymbol(String symbol, String status) {
        dslContext.insertInto(Tables.SYMBOLS)
                .set(Tables.SYMBOLS.SYMBOL, symbol)
                .set(Tables.SYMBOLS.BASE_ASSET, "BTC")
                .set(Tables.SYMBOLS.QUOTE_ASSET, "KRW")
                .set(Tables.SYMBOLS.STEP_SIZE, 1L)
                .set(Tables.SYMBOLS.STATUS, status)
                .set(Tables.SYMBOLS.CREATED_AT, Instant.now())
                .set(Tables.SYMBOLS.UPDATED_AT, Instant.now())
                .execute();
    }

    private void insertSymbolState(String symbol, long lastEventSequence) {
        dslContext.insertInto(Tables.SYMBOL_STATES)
                .set(Tables.SYMBOL_STATES.SYMBOL, symbol)
                .set(Tables.SYMBOL_STATES.LAST_EVENT_SEQUENCE, lastEventSequence)
                .set(Tables.SYMBOL_STATES.UPDATED_AT, Instant.now())
                .onConflict(Tables.SYMBOL_STATES.SYMBOL).doNothing()
                .execute();
    }

    private EngineSymbolState findBySymbol(List<EngineSymbolState> list, String symbol) {
        return list.stream()
                .filter(s -> s.symbol().equals(new Symbol(symbol)))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("findActiveSymbols()")
    class FindActiveSymbols {

        @Test
        @DisplayName("ACTIVE 심볼에 symbol_states 행이 있으면 lastEventSequence를 반환한다")
        void activeSymbolWithState_returnsLastEventSequence() {
            insertSymbol(SYM_A, "ACTIVE");
            insertSymbolState(SYM_A, 42L);

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(result).extracting(EngineSymbolState::symbol).contains(new Symbol(SYM_A));
            assertThat(findBySymbol(result, SYM_A).lastEventSequence()).isEqualTo(42L);
        }

        @Test
        @DisplayName("ACTIVE 심볼에 symbol_states 행이 없으면 lastEventSequence = 0을 반환한다")
        void activeSymbolWithoutState_returnsZeroSequence() {
            insertSymbol(SYM_A, "ACTIVE");

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(result).extracting(EngineSymbolState::symbol).contains(new Symbol(SYM_A));
            assertThat(findBySymbol(result, SYM_A).lastEventSequence()).isEqualTo(0L);
        }

        @Test
        @DisplayName("INACTIVE 심볼은 반환하지 않는다")
        void inactiveSymbol_excluded() {
            insertSymbol(SYM_A, "INACTIVE");

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(result).extracting(EngineSymbolState::symbol)
                    .doesNotContain(new Symbol(SYM_A));
        }

        @Test
        @DisplayName("여러 ACTIVE 심볼을 모두 반환한다")
        void multipleActiveSymbols_allReturned() {
            insertSymbol(SYM_A, "ACTIVE");
            insertSymbol(SYM_B, "ACTIVE");
            insertSymbolState(SYM_A, 10L);
            insertSymbolState(SYM_B, 20L);

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(result).extracting(EngineSymbolState::symbol)
                    .contains(new Symbol(SYM_A), new Symbol(SYM_B));
            assertThat(findBySymbol(result, SYM_A).lastEventSequence()).isEqualTo(10L);
            assertThat(findBySymbol(result, SYM_B).lastEventSequence()).isEqualTo(20L);
        }

        @Test
        @DisplayName("ACTIVE 심볼과 INACTIVE 심볼이 혼재할 때 ACTIVE만 반환한다")
        void mixedStatus_returnsOnlyActive() {
            insertSymbol(SYM_A, "ACTIVE");
            insertSymbol(SYM_B, "INACTIVE");

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(result).extracting(EngineSymbolState::symbol)
                    .contains(new Symbol(SYM_A))
                    .doesNotContain(new Symbol(SYM_B));
        }

        @Test
        @DisplayName("lastEventSequence가 0보다 큰 symbol_states를 올바르게 읽는다")
        void symbolStateWithLargeSequence_correctlyRead() {
            insertSymbol(SYM_C, "ACTIVE");
            insertSymbolState(SYM_C, Long.MAX_VALUE);

            List<EngineSymbolState> result = repository.findActiveSymbols();

            assertThat(findBySymbol(result, SYM_C).lastEventSequence()).isEqualTo(Long.MAX_VALUE);
        }
    }
}
