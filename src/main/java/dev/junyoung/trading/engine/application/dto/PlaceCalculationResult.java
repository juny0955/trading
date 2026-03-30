package dev.junyoung.trading.engine.application.dto;

import java.util.List;

import dev.junyoung.trading.engine.domain.model.PlaceRejectCode;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.shared.domain.value.Symbol;

public sealed interface PlaceCalculationResult
	permits PlaceCalculationResult.Accepted, PlaceCalculationResult.Rejected {

		record Accepted(
			Symbol symbol,
			long acceptedSeq,
			List<Order> updatedOrders,
			List<Trade> trades,
			List<BookOperation> bookOps
		) implements PlaceCalculationResult {
			public Accepted(Symbol symbol, long acceptedSeq, List<Order> updatedOrders) {
				this(symbol, acceptedSeq, updatedOrders, List.of(), List.of());
			}
		}

		record Rejected(
			Symbol symbol,
			long acceptedSeq,
			PlaceRejectCode reasonCode
		) implements PlaceCalculationResult {}

}
