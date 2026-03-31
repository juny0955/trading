package dev.junyoung.trading.engine.application.book;

import java.util.Collections;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

import dev.junyoung.trading.engine.domain.entity.OrderBook;
import dev.junyoung.trading.shared.domain.entity.OrderBookSnapshot;
import dev.junyoung.trading.shared.domain.value.Price;
import dev.junyoung.trading.shared.domain.value.Quantity;

public final class OrderBookSnapshotMapper {

	private OrderBookSnapshotMapper() {}

	/**
	 * {@link OrderBook}의 현재 상태를 읽어 불변 스냅샷을 생성한다.
	 * engine-thread에서만 호출해야 한다.
	 */
	public static OrderBookSnapshot from(OrderBook orderBook) {
		NavigableMap<Price, Quantity> bids = new TreeMap<>(Comparator.comparing(Price::value).reversed());
		bids.putAll(orderBook.bidsSnapshot());

		NavigableMap<Price, Quantity> asks = new TreeMap<>(Comparator.comparing(Price::value));
		asks.putAll(orderBook.asksSnapshot());

		return new OrderBookSnapshot(
			Collections.unmodifiableNavigableMap(bids),
			Collections.unmodifiableNavigableMap(asks)
		);
	}
}
