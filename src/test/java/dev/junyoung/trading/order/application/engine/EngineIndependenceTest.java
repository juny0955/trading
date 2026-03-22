package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.fixture.OrderFixture;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DisplayName("엔진 독립성 — BTC 블로킹 중 ETH 독립 처리")
class EngineIndependenceTest {

    @Test
    @DisplayName("BTC 엔진이 블로킹 중에도 ETH 엔진은 독립적으로 처리된다")
    void ethEngine_processesIndependently_whileBtcIsBlocked() throws InterruptedException {
        CountDownLatch btcBlocker   = new CountDownLatch(1);
        CountDownLatch ethProcessed = new CountDownLatch(1);

        EngineHandler btcHandler = mock(EngineHandler.class);
        doAnswer(_ -> { btcBlocker.await(10, SECONDS); return null; })
            .when(btcHandler).handle(any(EngineCommand.PlaceOrder.class));

        EngineHandler ethHandler = mock(EngineHandler.class);
        doAnswer(_ -> { ethProcessed.countDown(); return null; })
            .when(ethHandler).handle(any(EngineCommand.PlaceOrder.class));

        BlockingQueue<EngineCommand> btcQueue = new ArrayBlockingQueue<>(100);
        BlockingQueue<EngineCommand> ethQueue = new ArrayBlockingQueue<>(100);
        EngineRuntimeOwner runtimeOwner = mock(EngineRuntimeOwner.class);
        EngineLoop btcLoop = new EngineLoop(btcQueue, btcHandler, new EngineThread("BTC"), runtimeOwner);
        EngineLoop ethLoop = new EngineLoop(ethQueue, ethHandler, new EngineThread("ETH"), runtimeOwner);

        btcLoop.start();
        ethLoop.start();
        try {
            btcLoop.submit(btcPlaceOrder());
            ethLoop.submit(ethPlaceOrder());

            assertThat(ethProcessed.await(3, SECONDS)).isTrue();
        } finally {
            btcBlocker.countDown();
            btcLoop.stop();
            ethLoop.stop();
        }
    }

    private static EngineCommand.PlaceOrder btcPlaceOrder() {
        return new EngineCommand.PlaceOrder(
            OrderFixture.createLimit(Side.BUY, new Symbol("BTC"), TimeInForce.GTC, new Price(10_000), new Quantity(5)));
    }

    private static EngineCommand.PlaceOrder ethPlaceOrder() {
        return new EngineCommand.PlaceOrder(
            OrderFixture.createLimit(Side.BUY, new Symbol("ETH"), TimeInForce.GTC, new Price(10_000), new Quantity(5)));
    }
}
