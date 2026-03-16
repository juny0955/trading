package dev.junyoung.trading.order.adapter.out.persistence.jooq.order;

import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.OrdersRecord;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JooqOrderRepository implements OrderRepository {

    private final DSLContext dslContext;

    @Override
    public void save(Order order) {
        OrdersRecord record = JooqOrderMapper.toRecord(dslContext, order);

        dslContext.insertInto(Tables.ORDERS)
            .set(record)
            .onConflict(Tables.ORDERS.ORDER_ID)
            .doUpdate()
            .set(Tables.ORDERS.STATUS, record.getStatus())
            .set(Tables.ORDERS.REMAINING_QTY, record.getRemainingQty())
            .set(Tables.ORDERS.CUM_BASE_QTY, record.getCumBaseQty())
            .set(Tables.ORDERS.CUM_QUOTE_QTY, record.getCumQuoteQty())
            .set(Tables.ORDERS.UPDATED_AT, Instant.now())
            .execute();
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return dslContext.selectFrom(Tables.ORDERS)
            .where(Tables.ORDERS.ORDER_ID.eq(id.value()))
            .fetchOptional(JooqOrderMapper::toDomain);
    }

    @Override
    public Optional<Long> findMaxAcceptedSeq() {
        return dslContext.select(DSL.max(Tables.ORDERS.ACCEPTED_SEQ))
            .from(Tables.ORDERS)
            .fetchOptionalInto(Long.class);
    }

    @Override
    public void deleteById(OrderId orderId) {
        dslContext.deleteFrom(Tables.ORDERS)
            .where(Tables.ORDERS.ORDER_ID.eq(orderId.value()))
            .execute();
    }
}
