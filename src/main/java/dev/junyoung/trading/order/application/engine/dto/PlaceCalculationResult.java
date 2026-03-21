package dev.junyoung.trading.order.application.engine.dto;

import java.util.List;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public sealed interface PlaceCalculationResult
	permits PlaceCalculationResult.Accepted, PlaceCalculationResult.Rejected {

		record Accepted(
			Symbol symbol,
			long acceptedSeq,
			List<Order> updatedOrders,
			List<Trade> trades,
			List<BookOperation> bookOps
		) implements PlaceCalculationResult {}

		record Rejected(
			Symbol symbol,
			long acceptedSeq,
			PlaceRejectCode reasonCode
		) implements PlaceCalculationResult {}

}
