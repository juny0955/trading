package dev.junyoung.trading.order.application.engine.dto;

import java.util.List;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.Symbol;

public sealed interface CancelCalculationResult
	permits CancelCalculationResult.Cancelled,
	CancelCalculationResult.Skipped,
	CancelCalculationResult.Rejected {

	record Cancelled(
		Symbol symbol,
		long acceptedSeq,
		List<Order> updatedOrders,
		List<BookOperation> bookOps
	) implements CancelCalculationResult {}

	record Skipped(
		Symbol symbol,
		long acceptedSeq,
		CancelResultCode reasonCode
	) implements CancelCalculationResult {}

	record Rejected(
		Symbol symbol,
		long acceptedSeq,
		CancelResultCode reasonCode
	) implements CancelCalculationResult {}
}
