package dev.junyoung.trading.account.domain.value;

import java.util.Objects;

public record Asset(
	String value
) {
	public Asset {
		Objects.requireNonNull(value, "asset must not be null");
	}
}
