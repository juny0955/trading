package dev.junyoung.trading.order.application.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.NavigableMap;
import java.util.TreeMap;

import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.service.state.OrderBookView;

public class OrderBookViewFactory {

	public static OrderBookView create(OrderBook orderBook) {
		return new OrderBookView(
			deepCopyBook(orderBook.getBids()),
			deepCopyBook(orderBook.getAsks()),
			new HashMap<>(orderBook.getIndex())
		);
	}

	private static NavigableMap<Price, Deque<OrderId>> deepCopyBook(
		NavigableMap<Price, Deque<OrderId>> source
	) {
		NavigableMap<Price, Deque<OrderId>> copy = new TreeMap<>(source.comparator());
		source.forEach((price, queue) -> copy.put(price, new ArrayDeque<>(queue)));
		return copy;
	}
}
