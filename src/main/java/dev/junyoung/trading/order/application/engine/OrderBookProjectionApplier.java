package dev.junyoung.trading.order.application.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.order.application.engine.dto.BookOperation;
import dev.junyoung.trading.order.domain.model.OrderBook;

@Component
public class OrderBookProjectionApplier {

	public void apply(OrderBook orderBook, List<BookOperation> ops) {
		for (BookOperation op : ops) {
			switch (op) {
				case BookOperation.Add a -> orderBook.add(a.order());
				case BookOperation.Replace r -> orderBook.replaceOrder(r.updatedOrder());
				case BookOperation.Remove r -> orderBook.remove(r.orderId());
			}
		}
	}
}
