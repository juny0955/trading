package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.adapter.out.persistence.jooq.order.JooqOrderRepository;
import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.model.value.TradeId;
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

    @Nested
    @DisplayName("findByOrderId()")
    class FindByOrderId {

        @Test
        @DisplayName("buyOrderId가 일치하면 trade를 반환하고 주요 필드가 일치한다")
        void buyOrderMatches_returnsTrade() {
            buyOrder.activate();
            sellOrder.activate();
            Trade trade = Trade.of(buyOrder, sellOrder, QTY);
            tradeRepository.saveAll(List.of(trade));

            List<Trade> result = tradeRepository.findByOrderId(buyOrder.getOrderId());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().tradeId()).isEqualTo(trade.tradeId());
            assertThat(result.getFirst().symbol()).isEqualTo(SYMBOL);
            assertThat(result.getFirst().price()).isEqualTo(PRICE);
            assertThat(result.getFirst().quantity()).isEqualTo(QTY);
        }

        @Test
        @DisplayName("sellOrderId가 일치하면 trade를 반환한다")
        void sellOrderMatches_returnsTrade() {
            buyOrder.activate();
            sellOrder.activate();
            Trade trade = Trade.of(buyOrder, sellOrder, QTY);
            tradeRepository.saveAll(List.of(trade));

            List<Trade> result = tradeRepository.findByOrderId(sellOrder.getOrderId());

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().tradeId()).isEqualTo(trade.tradeId());
        }

        @Test
        @DisplayName("관련 trade가 없으면 빈 리스트를 반환한다")
        void noRelatedTrades_returnsEmptyList() {
            List<Trade> result = tradeRepository.findByOrderId(buyOrder.getOrderId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("여러 trade가 있으면 created_at 오름차순으로 정렬된다")
        void multipleTrades_sortedByCreatedAtAsc() {
            Instant earlier = Instant.parse("2026-03-01T00:00:00Z");
            Instant later = Instant.parse("2026-03-02T00:00:00Z");
            Trade trade1 = Trade.restore(
                    TradeId.newId(), SYMBOL,
                    buyOrder.getOrderId(), sellOrder.getOrderId(),
                    PRICE, new Quantity(5L), earlier
            );
            Trade trade2 = Trade.restore(
                    TradeId.newId(), SYMBOL,
                    buyOrder.getOrderId(), sellOrder.getOrderId(),
                    PRICE, new Quantity(3L), later
            );

            tradeRepository.saveAll(List.of(trade2, trade1));

            List<Trade> result = tradeRepository.findByOrderId(buyOrder.getOrderId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).createdAt()).isEqualTo(earlier);
            assertThat(result.get(1).createdAt()).isEqualTo(later);
        }
    }

    @Nested
    @DisplayName("findByAccountIdWithSide()")
    class FindByAccountIdWithSide {

        @Test
        @DisplayName("BUY account로 조회하면 side = BUY, buyOrderId를 반환한다")
        void buyAccount_returnsTradeWithBuySide() {
            buyOrder.activate();
            sellOrder.activate();
            Trade trade = Trade.of(buyOrder, sellOrder, QTY);
            tradeRepository.saveAll(List.of(trade));

            List<AccountTradeResult> result = tradeRepository.findByAccountIdWithSide(BUY_ACCOUNT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().side()).isEqualTo(Side.BUY);
            assertThat(result.getFirst().orderId()).isEqualTo(buyOrder.getOrderId());
        }

        @Test
        @DisplayName("SELL account로 조회하면 side = SELL, sellOrderId를 반환한다")
        void sellAccount_returnsTradeWithSellSide() {
            buyOrder.activate();
            sellOrder.activate();
            Trade trade = Trade.of(buyOrder, sellOrder, QTY);
            tradeRepository.saveAll(List.of(trade));

            List<AccountTradeResult> result = tradeRepository.findByAccountIdWithSide(SELL_ACCOUNT_ID);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().side()).isEqualTo(Side.SELL);
            assertThat(result.getFirst().orderId()).isEqualTo(sellOrder.getOrderId());
        }

        @Test
        @DisplayName("해당 account의 trade가 없으면 빈 리스트를 반환한다")
        void noTrades_returnsEmptyList() {
            AccountId unknownAccountId = new AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"));

            List<AccountTradeResult> result = tradeRepository.findByAccountIdWithSide(unknownAccountId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("BUY account로 조회 시 buy와 sell 양쪽 참여 거래를 모두 반환한다")
        void bothSidesParticipation_returnsAllTrades() {
            // BUY_ACCOUNT is buyer in trade1
            buyOrder.activate();
            sellOrder.activate();
            Trade trade1 = Trade.of(buyOrder, sellOrder, QTY);

            // BUY_ACCOUNT is seller in trade2 (use unique clientOrderIds to avoid duplicate key)
            Order anotherBuyOrder = Order.create(OrderId.newId(), SELL_ACCOUNT_ID, "client-buy-2", 1L,
                    SYMBOL, Side.BUY, OrderType.LIMIT, TimeInForce.GTC, PRICE, null, new Quantity(5L));
            Order anotherSellOrder = Order.create(OrderId.newId(), BUY_ACCOUNT_ID, "client-sell-2", 1L,
                    SYMBOL, Side.SELL, OrderType.LIMIT, TimeInForce.GTC, PRICE, null, new Quantity(5L));
            orderRepository.save(anotherBuyOrder);
            orderRepository.save(anotherSellOrder);
            anotherBuyOrder.activate();
            anotherSellOrder.activate();
            Trade trade2 = Trade.of(anotherBuyOrder, anotherSellOrder, new Quantity(5L));

            tradeRepository.saveAll(List.of(trade1, trade2));

            List<AccountTradeResult> result = tradeRepository.findByAccountIdWithSide(BUY_ACCOUNT_ID);

            assertThat(result).hasSize(2);
        }
    }
}
