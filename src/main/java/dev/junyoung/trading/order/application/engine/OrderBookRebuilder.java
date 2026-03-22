package dev.junyoung.trading.order.application.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderBookRebuilder {

	private final OrderRepository orderRepository;

	public List<Order> loadOpenOrders(Symbol symbol) {
		return orderRepository.findOpenOrdersBySymbol(symbol);
	}
}
