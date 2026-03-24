package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.account.application.port.out.BalanceRepository;
import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.order.adapter.out.cache.OrderBookSnapshot;
import dev.junyoung.trading.order.application.engine.book.OrderBookProjectionApplier;
import dev.junyoung.trading.order.application.engine.book.OrderBookRebuilder;
import dev.junyoung.trading.order.application.engine.handler.EngineResultPersistenceService;
import dev.junyoung.trading.order.application.engine.runtime.EngineRuntime;
import dev.junyoung.trading.order.application.port.out.IdempotencyKeyRepository;
import dev.junyoung.trading.order.application.port.out.OrderBookCachePort;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.application.service.EngineStartupRecoveryService;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import org.jooq.DSLContext;
import org.jooq.exception.NoDataFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DisplayName("EngineRestartIntegrationTest")
class EngineRestartIntegrationTest {

    private static final Symbol SYMBOL = new Symbol("TEST");
    private static final AccountId ACCOUNT_ID =
        new AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    private static final Asset KRW = new Asset("KRW");
    private static final Asset SYMBOL_ASSET = new Asset("TEST");

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private EngineStartupRecoveryService engineStartupRecoveryService;

    @Autowired
    private OrderBookCachePort orderBookCachePort;

    @Autowired
    private OrderBookProjectionApplier orderBookProjectionApplier;

    @Autowired
    private EngineResultPersistenceService engineResultPersistenceService;

    @Autowired
    private OrderBookRebuilder orderBookRebuilder;

    private EngineRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) runtime.stop();
    }

    @Test
    @DisplayName("restart recovery는 orphan ACCEPTED를 정리하고 open orders로 오더북과 held를 복구한다")
    void restartRecovery_rebuildsBookAndCleansOrphanAccepted() {
        seedAccount();

        Order newBuy = activeLimitOrder("new-buy", 1L, Side.BUY, 10_000L, 5L);
        Order partiallyFilledSell = activeLimitOrder("partial-sell", 2L, Side.SELL, 12_000L, 10L)
            .fill(new Quantity(4L), new Price(12_000L));
        Order orphanAccepted = acceptedLimitOrder("orphan-accepted", 3L, Side.BUY, 1_000L, 2L);

        orderRepository.save(newBuy);
        orderRepository.save(partiallyFilledSell);
        orderRepository.save(orphanAccepted);
        idempotencyKeyRepository.save(ACCOUNT_ID, orphanAccepted.getOrderId(), orphanAccepted.getClientOrderId());

        balanceRepository.save(ACCOUNT_ID, Balance.of(KRW, 100_000L, 52_000L));
        balanceRepository.save(ACCOUNT_ID, Balance.of(SYMBOL_ASSET, 10L, 6L));

        engineStartupRecoveryService.cleanupOrphanAccepted(SYMBOL);
        runtime = new EngineRuntime(
            SYMBOL,
            orderBookCachePort,
            orderBookProjectionApplier,
            engineResultPersistenceService,
            orderBookRebuilder
        );

        OrderBookSnapshot snapshot = orderBookCachePort.getSnapshot(SYMBOL);
        assertThat(snapshot.bids()).containsEntry(new Price(10_000L), new Quantity(5L));
        assertThat(snapshot.asks()).containsEntry(new Price(12_000L), new Quantity(6L));

        assertThat(orderRepository.findAcceptedOrdersBySymbol(SYMBOL)).isEmpty();

        Balance krwBalance = balanceRepository.findByAccountIdAndAsset(ACCOUNT_ID, KRW).orElseThrow();
        assertThat(krwBalance.getAvailable()).isEqualTo(102_000L);
        assertThat(krwBalance.getHeld()).isEqualTo(50_000L);

        Balance symbolBalance = balanceRepository.findByAccountIdAndAsset(ACCOUNT_ID, SYMBOL_ASSET).orElseThrow();
        assertThat(symbolBalance.getAvailable()).isEqualTo(10L);
        assertThat(symbolBalance.getHeld()).isEqualTo(6L);

        assertThatThrownBy(() -> idempotencyKeyRepository.findOrderId(ACCOUNT_ID, orphanAccepted.getClientOrderId()))
            .isInstanceOf(NoDataFoundException.class);

        Order orphanAfterRecovery = orderRepository.findById(orphanAccepted.getOrderId()).orElseThrow();
        assertThat(orphanAfterRecovery.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private void seedAccount() {
        dslContext.insertInto(Tables.ACCOUNTS)
            .set(Tables.ACCOUNTS.ACCOUNT_ID, ACCOUNT_ID.value())
            .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
            .execute();
    }

    private Order acceptedLimitOrder(String clientOrderId, long acceptedSeq, Side side, long price, long quantity) {
        return Order.create(
            OrderId.newId(),
            ACCOUNT_ID,
            clientOrderId,
            acceptedSeq,
            SYMBOL,
            side,
            dev.junyoung.trading.order.domain.model.enums.OrderType.LIMIT,
            TimeInForce.GTC,
            new Price(price),
            null,
            new Quantity(quantity)
        );
    }

    private Order activeLimitOrder(String clientOrderId, long acceptedSeq, Side side, long price, long quantity) {
        return acceptedLimitOrder(clientOrderId, acceptedSeq, side, price, quantity).activate();
    }
}
