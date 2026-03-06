package dev.junyoung.trading.order.domain.model.entity;

import java.util.Objects;

import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;

/**
 * 매칭된 단일 체결 결과.
 *
 * 외부 진입점은 {@link #of(Order, Order, Quantity)}이다.
 */
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

	/** taker/maker 주문과 체결 수량으로 체결 레코드를 생성한다. */
	public static Trade of(Order taker, Order maker, Quantity qty) {
		boolean isBuy = taker.getSide() == Side.BUY;
		return new Trade(
			isBuy ? taker.getOrderId() : maker.getOrderId(),
			isBuy ? maker.getOrderId() : taker.getOrderId(),
			maker.getLimitPriceOrThrow(),
			qty
		);
	}
}
