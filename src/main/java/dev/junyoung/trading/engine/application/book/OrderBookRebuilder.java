package dev.junyoung.trading.engine.application.book;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.engine.application.metrics.ReplayMetrics;
import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderBookRebuilder {

	private final OrderRepository orderRepository;
	private final ReplayMetrics replayMetrics;

	public List<Order> loadOpenOrders(Symbol symbol) {
		List<Order> orders = orderRepository.findOpenOrdersBySymbol(symbol);
		replayMetrics.incrementRestoredOrderCount(orders.size());
		return orders;
	}
}
