package dev.junyoung.trading.engine.domain.service.dto;

import dev.junyoung.trading.engine.domain.service.state.OrderBookView;
import dev.junyoung.trading.order.domain.model.entity.Order;

public record PlaceCalculationInput(OrderBookView view, Order taker) {
}
