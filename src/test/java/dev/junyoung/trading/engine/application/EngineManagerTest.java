package dev.junyoung.trading.engine.application;

import dev.junyoung.trading.common.props.TradingProperties;
import dev.junyoung.trading.engine.application.book.OrderBookProjectionApplier;
import dev.junyoung.trading.engine.application.book.OrderBookRebuilder;
import dev.junyoung.trading.engine.application.service.EngineResultPersistenceService;
import dev.junyoung.trading.order.application.exception.UnsupportedSymbolException;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import dev.junyoung.trading.engine.application.metrics.ReplayMetrics;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.engine.application.service.EngineStartupRecoveryService;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Mock
    private EngineMetrics engineMetrics;

    @Mock
    private ReplayMetrics replayMetrics;

    private EngineManager engineManager;

    @AfterEach
    void tearDown() {
        if (engineManager != null) engineManager.stop();
    }

    private Order placeOrder(String symbol) {
        Symbol sym = new Symbol(symbol);
        return OrderFixture.createLimit(Side.BUY, sym, TimeInForce.GTC, new Price(10_000), new Quantity(5));
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
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
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
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
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
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertThatCode(() -> engineManager.submitPlace(new Symbol("BTC"), placeOrder("BTC"), Instant.now())).doesNotThrowAnyException();
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
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertThrows(
                UnsupportedSymbolException.class,
                () -> engineManager.submitPlace(new Symbol("XRP"), placeOrder("XRP"), Instant.now())
            );
        }
    }


    // ── ThreadConcurrency ────────────────────────────────────────────────

    @Nested
    @DisplayName("스레드 동시성")
    class ThreadConcurrency {

        @BeforeEach
        void setUp() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH"));
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();
        }

        @Test
        @DisplayName("N개 스레드가 동시에 submit해도 모든 주문이 정상 수신된다")
        void concurrentSubmit_allOrdersAccepted() throws InterruptedException {
            int threadCount = 20;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        engineManager.submitPlace(new Symbol("BTC"), placeOrder("BTC"), Instant.now());
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startGate.countDown();
            assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("BTC·ETH 양쪽에 동시 submit해도 모든 주문이 정상 수신된다")
        void concurrentSubmit_multipleSymbols_allAccepted() throws InterruptedException {
            int perSymbol = 10;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(perSymbol * 2);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < perSymbol; i++) {
                new Thread(() -> {
                    try {
                        startGate.await();
                        engineManager.submitPlace(new Symbol("BTC"), placeOrder("BTC"), Instant.now());
                        successCount.incrementAndGet();
                    } catch (Exception ignored) { } finally { doneLatch.countDown(); }
                }).start();
                new Thread(() -> {
                    try {
                        startGate.await();
                        engineManager.submitPlace(new Symbol("ETH"), placeOrder("ETH"), Instant.now());
                        successCount.incrementAndGet();
                    } catch (Exception ignored) { } finally { doneLatch.countDown(); }
                }).start();
            }

            startGate.countDown();
            assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(successCount.get()).isEqualTo(perSymbol * 2);
        }
    }

    // ── stop() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        @DisplayName("심볼 없이 시작한 뒤 stop()은 예외 없이 완료된다")
        void stop_noSymbols_doesNotThrow() {
            when(tradingProperties.getSymbols()).thenReturn(List.of());
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertDoesNotThrow(() -> engineManager.stop());
        }

        @Test
        @DisplayName("단일 심볼 엔진을 정상 종료한다")
        void stop_singleSymbol_terminatesGracefully() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertDoesNotThrow(() -> engineManager.stop());
        }

        @Test
        @DisplayName("복수 심볼의 모든 엔진을 정상 종료한다")
        void stop_multipleSymbols_allTerminateGracefully() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC", "ETH", "SOL"));
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertDoesNotThrow(() -> engineManager.stop());
        }

        @Test
        @DisplayName("stop()을 여러 번 호출해도 예외가 발생하지 않는다")
        void stop_calledMultipleTimes_doesNotThrow() {
            when(tradingProperties.getSymbols()).thenReturn(List.of("BTC"));
            engineManager = new EngineManager(
                tradingProperties,
                engineStartupRecoveryService,
                orderBookCachePort,
                engineResultPersistenceService,
                orderBookProjectionApplier,
                orderBookRebuilder,
                engineMetrics,
                replayMetrics
            );
            engineManager.start();

            assertDoesNotThrow(() -> {
                engineManager.stop();
                engineManager.stop();
            });
        }
    }
}
