package dev.junyoung.trading.order.domain.model.entity;

import java.util.Objects;

import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

public record Trade(
	OrderId buyOrderId,
	OrderId sellOrderId,
	Price executionPrice,
	Quantity executedQty
) {
	public Trade {
		Objects.requireNonNull(buyOrderId, "buyOrderId");
		Objects.requireNonNull(sellOrderId, "sellOrderId");
		Objects.requireNonNull(executionPrice, "executionPrice");
		Objects.requireNonNull(executedQty, "executedQty");
	}

	public static Trade of(Order taker, Order maker, Quantity qty) {
		boolean isBuy = taker.getSide() == Side.BUY;
		return new Trade(
			isBuy ? taker.getOrderId() : maker.getOrderId(),
			isBuy ? maker.getOrderId() : taker.getOrderId(),
			maker.getPrice(),
			qty
		);
	}
}
