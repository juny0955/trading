package dev.junyoung.trading.order.adapter.out.persistence.jooq.idempotencyKey;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.order.adapter.out.persistence.jooq.order.JooqOrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.fixture.OrderFixture;

@SpringBootTest
@Transactional
@DisplayName("JooqIdempotencyKeyRepository")
class JooqIdempotencyKeyRepositoryTest {

    @Autowired
    DSLContext dslContext;

    @Autowired
    JooqIdempotencyKeyRepository repository;

    @Autowired
    JooqOrderRepository orderRepository;

    private static final AccountId ACCOUNT_ID = OrderFixture.DEFAULT_ACCOUNT_ID;
    private static final AccountId OTHER_ACCOUNT_ID =
            new AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Symbol SYMBOL = new Symbol("BTCUSDT");
    private static final Price PRICE = new Price(50_000L);
    private static final Quantity QUANTITY = new Quantity(10L);

    @BeforeEach
    void setUp() {
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
        dslContext.insertInto(Tables.ACCOUNTS)
                .set(Tables.ACCOUNTS.ACCOUNT_ID, OTHER_ACCOUNT_ID.value())
                .set(Tables.ACCOUNTS.CREATED_AT, Instant.now())
                .execute();
    }

    private OrderId savedOrderId(AccountId accountId) {
        OrderId orderId = OrderId.newId();
        Order order = Order.create(orderId, accountId, orderId.value().toString(), 1L,
                SYMBOL, Side.BUY, OrderType.LIMIT, TimeInForce.GTC, PRICE, null, QUANTITY);
        orderRepository.save(order);
        return orderId;
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("저장된 키는 findOrderId()로 조회할 수 있다")
        void save_persists_findable() {
            OrderId orderId = savedOrderId(ACCOUNT_ID);

            repository.save(ACCOUNT_ID, orderId, "key-001");

            assertThat(repository.findOrderId(ACCOUNT_ID, "key-001")).isEqualTo(orderId);
        }

        @Test
        @DisplayName("동일 (accountId, clientOrderId)로 두 번 저장하면 DuplicateKeyException이 발생한다")
        void save_duplicateKey_throwsDuplicateKeyException() {
            OrderId orderId1 = savedOrderId(ACCOUNT_ID);
            OrderId orderId2 = savedOrderId(ACCOUNT_ID);
            repository.save(ACCOUNT_ID, orderId1, "dup-key");

            assertThatThrownBy(() -> repository.save(ACCOUNT_ID, orderId2, "dup-key"))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        @DisplayName("서로 다른 account는 같은 clientOrderId를 사용해도 충돌하지 않는다")
        void save_differentAccounts_sameClientOrderId_noDuplicate() {
            OrderId orderId1 = savedOrderId(ACCOUNT_ID);
            OrderId orderId2 = savedOrderId(OTHER_ACCOUNT_ID);

            assertThatCode(() -> {
                repository.save(ACCOUNT_ID, orderId1, "shared-key");
                repository.save(OTHER_ACCOUNT_ID, orderId2, "shared-key");
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("findOrderId()")
    class FindOrderId {

        @Test
        @DisplayName("저장된 (accountId, clientOrderId)로 정확한 orderId를 반환한다")
        void findOrderId_returnsCorrectOrderId() {
            OrderId orderId = savedOrderId(ACCOUNT_ID);
            repository.save(ACCOUNT_ID, orderId, "find-key");

            assertThat(repository.findOrderId(ACCOUNT_ID, "find-key")).isEqualTo(orderId);
        }

        @Test
        @DisplayName("같은 clientOrderId라도 다른 account의 orderId는 반환하지 않는다")
        void findOrderId_doesNotCrossAccounts() {
            OrderId orderId1 = savedOrderId(ACCOUNT_ID);
            OrderId orderId2 = savedOrderId(OTHER_ACCOUNT_ID);
            repository.save(ACCOUNT_ID, orderId1, "cross-key");
            repository.save(OTHER_ACCOUNT_ID, orderId2, "cross-key");

            assertThat(repository.findOrderId(ACCOUNT_ID, "cross-key")).isEqualTo(orderId1);
            assertThat(repository.findOrderId(OTHER_ACCOUNT_ID, "cross-key")).isEqualTo(orderId2);
        }
    }
}
