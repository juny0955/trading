package dev.junyoung.trading.order.application.port.out;

import dev.junyoung.trading.order.domain.model.entity.Trade;

import java.util.List;

public interface TradeRepository {
    void saveAll(List<Trade> trades);
}
