package dev.junyoung.trading.order.adapter.in.rest.request;

import dev.junyoung.trading.common.validation.ValidEnum;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
	@NotBlank
    String symbol,

	@NotBlank
	@ValidEnum(enumClass = Side.class)
    String side,

	@NotBlank
	@ValidEnum(enumClass = OrderType.class)
    String orderType,

    Long price,

	@NotNull
	@Min(1)
    Long quantity
) {
}
