package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import org.jooq.DSLContext;

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
}
