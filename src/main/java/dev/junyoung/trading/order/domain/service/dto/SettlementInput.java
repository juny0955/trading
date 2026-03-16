package dev.junyoung.trading.order.domain.service.dto;

import java.util.List;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;

public record SettlementInput(
	List<Order> updatedOrders,
	List<Trade> trades
) {
}
