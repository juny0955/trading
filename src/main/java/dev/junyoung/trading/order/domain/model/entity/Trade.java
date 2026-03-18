package dev.junyoung.trading.order.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.*;

/**
 * 매칭된 단일 체결 결과.
 * <p>
 * 외부 진입점은 {@link #of(Order, Order, Quantity)}이다.
 * </p>
 */
public record Trade(
	TradeId tradeId,
	Symbol symbol,
	OrderId buyOrderId,
	OrderId sellOrderId,
	Price price,
	Quantity quantity,
	Instant createdAt
) {
	public Trade {
		Objects.requireNonNull(tradeId, "tradeId must not be null");
		Objects.requireNonNull(symbol, "symbol must not be null");
		Objects.requireNonNull(buyOrderId, "buyOrderId must not be null");
		Objects.requireNonNull(sellOrderId, "sellOrderId must not be null");
		Objects.requireNonNull(price, "price must not be null");
		Objects.requireNonNull(quantity, "quantity must not be null");
	}

	/** taker/maker 주문과 체결 수량으로 체결 레코드를 생성한다. */
	public static Trade of(Order taker, Order maker, Quantity qty) {
		boolean isBuy = taker.getSide() == Side.BUY;
		return new Trade(
			TradeId.newId(),
			taker.getSymbol(),
			isBuy ? taker.getOrderId() : maker.getOrderId(),
			isBuy ? maker.getOrderId() : taker.getOrderId(),
			maker.getLimitPriceOrThrow(),
			qty,
			Instant.now()
		);
	}

	public static Trade restore(
		TradeId tradeId,
		Symbol symbol,
		OrderId buyOrderId,
		OrderId sellOrderId,
		Price price,
		Quantity quantity,
		Instant createdAt
	) {
		return new Trade(
			tradeId,
			symbol,
			buyOrderId,
			sellOrderId,
			price,
			quantity,
			createdAt
		);
	}
}
