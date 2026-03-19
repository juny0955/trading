package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.order.application.port.out.result.AccountTradeResult;
import dev.junyoung.trading.jooq.Tables;
import dev.junyoung.trading.jooq.tables.Orders;
import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JooqTradeRepository implements TradeRepository {

    private final DSLContext dslContext;

    @Override
    public void saveAll(List<Trade> trades) {
        List<TradesRecord> records = trades.stream()
            .map(t -> JooqTradeMapper.toRecord(dslContext, t))
            .toList();

        dslContext.batchInsert(records).execute();
    }

    @Override
    public List<Trade> findByOrderId(OrderId orderId) {
        return dslContext.selectFrom(Tables.TRADES)
            .where(Tables.TRADES.BUY_ORDER_ID.eq(orderId.value()))
                .or(Tables.TRADES.SELL_ORDER_ID.eq(orderId.value()))
            .orderBy(Tables.TRADES.CREATED_AT.asc(), Tables.TRADES.TRADE_ID.asc())
            .fetch(JooqTradeMapper::toDomain);
    }

    @Override
    public List<AccountTradeResult> findByAccountIdWithSide(AccountId accountId) {
        Orders buyOrder = Tables.ORDERS.as("bo");
        Orders sellOrder = Tables.ORDERS.as("so");

        return dslContext.select(
                Tables.TRADES.TRADE_ID,
                Tables.TRADES.SYMBOL,
                DSL.when(buyOrder.ACCOUNT_ID.eq(accountId.value()), Tables.TRADES.BUY_ORDER_ID)
                    .otherwise(Tables.TRADES.SELL_ORDER_ID),
                DSL.when(buyOrder.ACCOUNT_ID.eq(accountId.value()), DSL.inline(Side.BUY.name()))
                    .otherwise(DSL.inline(Side.SELL.name())),
                Tables.TRADES.PRICE,
                Tables.TRADES.QUANTITY,
                Tables.TRADES.CREATED_AT
            )
            .from(Tables.TRADES)
            .join(buyOrder).on(buyOrder.ORDER_ID.eq(Tables.TRADES.BUY_ORDER_ID))
            .join(sellOrder).on(sellOrder.ORDER_ID.eq(Tables.TRADES.SELL_ORDER_ID))
            .where(buyOrder.ACCOUNT_ID.eq(accountId.value()))
                .or(sellOrder.ACCOUNT_ID.eq(accountId.value()))
            .orderBy(Tables.TRADES.CREATED_AT.asc(), Tables.TRADES.TRADE_ID.asc())
            .fetch(JooqTradeMapper::toAccountTradeResult);
    }
}
