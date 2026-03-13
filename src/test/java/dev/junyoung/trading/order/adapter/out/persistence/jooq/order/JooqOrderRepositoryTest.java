package dev.junyoung.trading.order.adapter.out.persistence.jooq.order;

import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("JooqOrderRepository")
class JooqOrderRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqOrderRepository repository;

    private static final Symbol SYMBOL = new Symbol("BTCUSDT");
    private static final Price PRICE = new Price(50_000L);
    private static final Quantity QUANTITY = new Quantity(10L);

    @BeforeEach
    void setUp() {
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, OrderFixture.DEFAULT_ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("새 LIMIT 주문을 저장하면 findById()로 주요 필드를 조회할 수 있다")
        void saveNewLimitOrder() {
            Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QUANTITY);

            repository.save(order);

            Optional<Order> found = repository.findById(order.getOrderId());
            assertThat(found).isPresent();

            Order saved = found.get();
            assertThat(saved.getOrderId()).isEqualTo(order.getOrderId());
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(saved.getSymbol()).isEqualTo(SYMBOL);
            assertThat(saved.getSide()).isEqualTo(Side.BUY);
            assertThat(saved.getPriceValue()).contains(PRICE.value());
            assertThat(saved.getQuantity()).isEqualTo(QUANTITY);
        }

        @Test
        @DisplayName("저장된 주문에 activate() 후 재저장하면 상태가 NEW로 변경된다")
        void upsertUpdatesStatusAfterActivate() {
            Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QUANTITY);
            repository.save(order);

            order.activate();
            repository.save(order);

            Order updated = repository.findById(order.getOrderId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("activate() 후 fill() 하고 재저장하면 remaining과 status가 변경된다")
        void upsertUpdatesRemainingAfterFill() {
            Order order = OrderFixture.createLimit(Side.BUY, SYMBOL, TimeInForce.GTC, PRICE, QUANTITY);
            repository.save(order);

            order.activate();
            Quantity fillQty = new Quantity(3L);
            order.fill(fillQty);
            repository.save(order);

            Order updated = repository.findById(order.getOrderId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(updated.getRemaining()).isEqualTo(new Quantity(7L));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("저장된 주문 ID로 조회하면 Optional에 값이 있다")
        void returnsOrderWhenExists() {
            Order order = OrderFixture.createLimit(Side.SELL, SYMBOL, TimeInForce.GTC, PRICE, QUANTITY);
            repository.save(order);

            Optional<Order> result = repository.findById(order.getOrderId());

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 OrderId로 조회하면 Optional.empty()를 반환한다")
        void returnsEmptyWhenNotFound() {
            OrderId unknownId = new OrderId(UUID.randomUUID());

            Optional<Order> result = repository.findById(unknownId);

            assertThat(result).isEmpty();
        }
    }
}
