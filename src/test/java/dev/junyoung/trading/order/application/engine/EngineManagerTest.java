package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.common.props.TradingProperties;
import dev.junyoung.trading.order.application.engine.book.OrderBookProjectionApplier;
import dev.junyoung.trading.order.application.engine.book.OrderBookRebuilder;
import dev.junyoung.trading.order.application.engine.handler.EngineResultPersistenceService;
import dev.junyoung.trading.order.application.engine.loop.EngineCommand;
import dev.junyoung.trading.order.application.exception.order.UnsupportedSymbolException;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.application.service.EngineStartupRecoveryService;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineManager")
class EngineManagerTest {

    @Mock
    private TradingProperties tradingProperties;

    @Mock
    private OrderBookCachePort orderBookCachePort;

    @Mock
    private EngineResultPersistenceService engineResultPersistenceService;

    @Mock
    private OrderBookProjectionApplier orderBookProjectionApplier;

    @Mock
    private OrderBookRebuilder orderBookRebuilder;

    @Mock
    private EngineStartupRecoveryService engineStartupRecoveryService;

    private EngineManager engineManager;

    @AfterEach
    void tearDown() {
        if (engineManager != null) engineManager.stop();
    }

    private EngineCommand.PlaceOrder placeOrder(String symbol) {
        Symbol sym = new Symbol(symbol);
        Order order = OrderFixture.createLimit(Side.BUY, sym, TimeInForce.GTC, new Price(10_000), new Quantity(5));
        return new EngineCommand.PlaceOrder(order);
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("빈 심볼 목록이어도 예외 없이 시작된다")
        void start_emptySymbols_doesNotThrow() {
            when(tradingProperties.getSymbols()).thenReturn(List.of());
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder
            );

            assertThatCode(() -> engineManager.start()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("시작 시 orphan 정리 후 open order rebuild를 수행한다")
        void start_cleansUpOrphansBeforeReplay() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
            when(orderBookRebuilder.loadOpenOrders(new Symbol("BTC"))).thenReturn(List.of());
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder
            );

            engineManager.start();

            Symbol symbol = new Symbol("BTC");
            InOrder inOrder = inOrder(engineStartupRecoveryService, orderBookRebuilder);
            inOrder.verify(engineStartupRecoveryService).cleanupOrphanAccepted(symbol);
            inOrder.verify(orderBookRebuilder).loadOpenOrders(symbol);
        }
    }

    @Nested
    @DisplayName("submit()")
    class Submit {

        @Test
        @DisplayName("등록된 심볼에는 submit()이 전달된다")
        void submit_knownSymbol_doesNotThrow() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH"));
            when(orderBookRebuilder.loadOpenOrders(new Symbol("BTC"))).thenReturn(List.of());
            when(orderBookRebuilder.loadOpenOrders(new Symbol("ETH"))).thenReturn(List.of());
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder
            );
            engineManager.start();

            assertThatCode(() -> engineManager.submit(new Symbol("BTC"), placeOrder("BTC"))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("등록되지 않은 심볼은 UnsupportedSymbolException을 던진다")
        void submit_unknownSymbol_throwsUnsupportedSymbolException() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
            when(orderBookRebuilder.loadOpenOrders(new Symbol("BTC"))).thenReturn(List.of());
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder
            );
            engineManager.start();

            assertThrows(
                UnsupportedSymbolException.class,
                () -> engineManager.submit(new Symbol("XRP"), placeOrder("XRP"))
            );
        }
    }
}
