package dev.junyoung.trading.account.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

import java.util.Objects;

public record Asset(
	String value
) {
	public Asset {
		Objects.requireNonNull(value, "asset must not be null");
		if (value.isBlank())
			throw new BusinessRuleException("ASSET_BLANK", "asset must not be blank");

		value = value.trim().toUpperCase();
	}
}
