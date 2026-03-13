package dev.junyoung.trading.order.adapter.out.persistence.jooq.order;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.OrdersRecord;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.*;
import org.jooq.DSLContext;

final class JooqOrderMapper {

    static Order toDomain(OrdersRecord record) {
        return Order.restore(
            new OrderId(record.getOrderId()),
            new AccountId(record.getAccountId()),
            record.getClientOrderId(),
            record.getAcceptedSeq(),
            Side.valueOf(record.getSide()),
            new Symbol(record.getSymbol()),
            OrderType.valueOf(record.getOrderType()),
            TimeInForce.valueOf(record.getTif()),
            nullablePrice(record),
            nullableQuoteQty(record.getQuoteQty()),
            nullableQuantity(record.getQuantity()),
            new Quantity(record.getRemainingQty()),
            OrderStatus.valueOf(record.getStatus()),
            new QuoteQty(record.getCumQuoteQty()),
            new Quantity(record.getCumBaseQty()),
            record.getOrderedAt(),
            record.getCreatedAt(),
            record.getUpdatedAt()
        );
    }

    static OrdersRecord toRecord(DSLContext dslContext, Order order) {
        OrdersRecord record = dslContext.newRecord(Tables.ORDERS);
        record.setOrderId(order.getOrderId().value());
        record.setAccountId(order.getAccountId().value());
        record.setClientOrderId(order.getClientOrderId());
        record.setAcceptedSeq(order.getAcceptedSeq());
        record.setSymbol(order.getSymbol().value());
        record.setSide(order.getSide().name());
        record.setOrderType(order.getOrderType().name());
        record.setTif(order.getTif().name());
        record.setStatus(order.getStatus().name());
        record.setPrice(order.getPriceValue().orElse(null));
        record.setQuantity(order.getQuantityValue().orElse(null));
        record.setRemainingQty(order.getRemaining().value());
        record.setQuoteQty(order.getQuoteQty() != null ? order.getQuoteQty().value() : null);
        record.setCumBaseQty(order.getCumBaseQty().value());
        record.setCumQuoteQty(order.getCumQuoteQty().value());
        record.setOrderedAt(order.getOrderedAt());
        return record;
    }

    private static Price nullablePrice(OrdersRecord record) {
        Long value = record.getPrice();
        return value == null ? null : new Price(value);
    }

    private static QuoteQty nullableQuoteQty(Long value) {
        return value == null ? null : new QuoteQty(value);
    }

    private static Quantity nullableQuantity(Long value) {
        return value == null ? null : new Quantity(value);
    }

}
