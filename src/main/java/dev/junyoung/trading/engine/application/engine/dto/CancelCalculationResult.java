package dev.junyoung.trading.engine.domain.model;

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
		Long acceptedSeq,
		CancelResultCode reasonCode
	) implements CancelCalculationResult {}

	record Rejected(
		Symbol symbol,
		Long acceptedSeq,
		CancelResultCode reasonCode
	) implements CancelCalculationResult {}
}
