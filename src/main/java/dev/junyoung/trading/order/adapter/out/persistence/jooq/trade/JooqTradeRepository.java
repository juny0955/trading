package dev.junyoung.trading.order.adapter.out.persistence.jooq.trade;

import dev.junyoung.trading.jooq.tables.records.TradesRecord;
import dev.junyoung.trading.order.application.port.out.TradeRepository;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
