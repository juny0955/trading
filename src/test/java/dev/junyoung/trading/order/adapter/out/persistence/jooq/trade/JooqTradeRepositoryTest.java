package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.adapter.out.persistence.jooq.order.JooqOrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional
@DisplayName("JooqTradeRepository")
class JooqTradeRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqTradeRepository tradeRepository;

    @Autowired
    JooqOrderRepository orderRepository;

    private static final Symbol SYMBOL = new Symbol("BTCUSDT");
    private static final Price PRICE = new Price(50_000L);
    private static final Quantity QTY = new Quantity(10L);

    private static final AccountId BUY_ACCOUNT_ID = OrderFixture.DEFAULT_ACCOUNT_ID;
    private static final AccountId SELL_ACCOUNT_ID =
            new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private Order buyOrder;
    private Order sellOrder;

    @BeforeEach
    void setUp() {
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, BUY_ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();

        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, SELL_ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();

        buyOrder = OrderFixture.createLimit(BUY_ACCOUNT_ID, Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QTY);
        sellOrder = OrderFixture.createLimit(SELL_ACCOUNT_ID, Side.SELL, SYMBOL, TimeInForce.GTC, PRICE, QTY);

        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);
    }

    @Nested
    @DisplayName("updateAll()")
    class SaveAll {

        @Test
        @DisplayName("단건 Trade 저장 시 TRADES 행 1건이 삽입되고 주요 필드가 일치한다")
        void savesSingleTrade() {
            buyOrder.activate();
            sellOrder.activate();
            Trade trade = Trade.of(buyOrder, sellOrder, QTY);

            tradeRepository.saveAll(List.of(trade));

            List<TradesRecord> rows = dslContext.selectFrom(Tables.TRADES).fetchInto(TradesRecord.class);
            assertThat(rows).hasSize(1);

            TradesRecord row = rows.getFirst();
            assertThat(row.getTradeId()).isEqualTo(trade.tradeId().value());
            assertThat(row.getSymbol()).isEqualTo(SYMBOL.value());
            assertThat(row.getBuyOrderId()).isEqualTo(buyOrder.getOrderId().value());
            assertThat(row.getSellOrderId()).isEqualTo(sellOrder.getOrderId().value());
            assertThat(row.getPrice()).isEqualTo(PRICE.value());
            assertThat(row.getQuantity()).isEqualTo(QTY.value());
        }

        @Test
        @DisplayName("두 건 Trade 저장 시 TRADES 행 2건이 삽입된다")
        void savesMultipleTrades() {
            buyOrder.activate();
            sellOrder.activate();

            Quantity halfQty = new Quantity(5L);
            Trade t1 = Trade.of(buyOrder, sellOrder, halfQty);
            Trade t2 = Trade.of(buyOrder, sellOrder, halfQty);

            tradeRepository.saveAll(List.of(t1, t2));

            List<TradesRecord> rows = dslContext.selectFrom(Tables.TRADES).fetchInto(TradesRecord.class);
            assertThat(rows).hasSize(2);
        }

        @Test
        @DisplayName("빈 리스트 전달 시 예외 없이 처리되고 TRADES 행이 없다")
        void savesEmptyListWithoutError() {
            assertThatCode(() -> tradeRepository.saveAll(List.of()))
                    .doesNotThrowAnyException();

            List<TradesRecord> rows = dslContext.selectFrom(Tables.TRADES).fetchInto(TradesRecord.class);
            assertThat(rows).isEmpty();
        }
    }
}
