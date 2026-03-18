package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import java.time.Instant;
import java.util.UUID;

import org.jooq.Record7;
import org.jooq.DSLContext;

import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.order.domain.model.value.TradeId;

final class JooqTradeMapper {

    static TradesRecord toRecord(DSLContext dslContext, Trade trade) {
        TradesRecord record = dslContext.newRecord(Tables.TRADES);
        record.setTradeId(trade.tradeId().value());
        record.setSymbol(trade.symbol().value());
        record.setBuyOrderId(trade.buyOrderId().value());
        record.setSellOrderId(trade.sellOrderId().value());
        record.setPrice(trade.price().value());
        record.setQuantity(trade.quantity().value());
        record.setCreatedAt(trade.createdAt());
        return record;
    }

    static Trade toDomain(TradesRecord record) {
        return Trade.restore(
            new TradeId(record.getTradeId()),
            new Symbol(record.getSymbol()),
            new OrderId(record.getBuyOrderId()),
            new OrderId(record.getSellOrderId()),
            new Price(record.getPrice()),
            new Quantity(record.getQuantity()),
            record.getCreatedAt()
        );
    }

    static AccountTradeResult toAccountTradeResult(Record7<UUID, String, UUID, String, Long, Long, Instant> record) {
        return new AccountTradeResult(
            new TradeId(record.value1()),
            new Symbol(record.value2()),
            new OrderId(record.value3()),
            Side.valueOf(record.value4()),
            new Price(record.value5()),
            new Quantity(record.value6()),
            record.value7()
        );
    }
}
