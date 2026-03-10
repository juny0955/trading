package dev.junyoung.trading.order.adapter.in.rest.request;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.common.validation.annotation.ValidEnum;
import dev.junyoung.trading.order.adapter.in.rest.validation.annotation.ValidPlaceOrder;
import dev.junyoung.trading.order.application.port.in.command.PlaceOrderCommand;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@ValidPlaceOrder
public record PlaceOrderRequest(
	@NotBlank
    String symbol,

	@NotBlank
	@ValidEnum(enumClass = Side.class)
    String side,

	@NotBlank
	@ValidEnum(enumClass = OrderType.class)
    String orderType,

	@ValidEnum(enumClass = TimeInForce.class)
	String tif,

	@Min(1)
    Long price,

	@Min(1)
	Long quoteQty,

	@Min(1)
    Long quantity,

	@NotBlank
	String clientOrderId
) {
	public PlaceOrderCommand toCommand(String accountId) {
		return new PlaceOrderCommand(
			AccountId.from(accountId),
			new Symbol(symbol),
			Side.valueOf(side),
			OrderType.valueOf(orderType),
			tif == null ? null : TimeInForce.valueOf(tif),
			price == null ? null : new Price(price),
			quoteQty == null ? null : new QuoteQty(quoteQty),
			quantity == null ? null : new Quantity(quantity),
			clientOrderId
		);
	}
}
