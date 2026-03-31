package dev.junyoung.trading.engine.application.book;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.junyoung.trading.engine.application.dto.BookOperation;
import dev.junyoung.trading.engine.domain.entity.OrderBook;

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
