package dev.junyoung.trading.order.domain.service.dto;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.service.state.OrderBookView;

public record PlaceCalculationInput(OrderBookView view, Order taker) {
}
