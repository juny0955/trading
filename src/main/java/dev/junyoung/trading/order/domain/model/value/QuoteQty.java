package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

/** MARKET BUY 예산 금액 (0 이상의 정수). */
public record QuoteQty(
	long value
) {
	public QuoteQty {
		if (value < 0)
			throw new BusinessRuleException("QUOTE_QTY_NEGATIVE", "value must be positive");
	}
}
