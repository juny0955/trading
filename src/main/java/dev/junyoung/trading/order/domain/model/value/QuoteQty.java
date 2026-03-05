package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

public record QuoteQty(
	long value
) {
	public QuoteQty {
		if (value < 0)
			throw new BusinessRuleException("QUOTE_QTY_NEGATIVE", "value must be positive");
	}
}
