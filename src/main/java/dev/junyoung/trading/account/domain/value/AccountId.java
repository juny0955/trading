package dev.junyoung.trading.account.domain.value;

import java.util.Objects;
import java.util.UUID;

public record AccountId(
	UUID value
) {
	public AccountId {
		Objects.requireNonNull(value, "accountId must not be null");
	}

	/* 새로운 무작위 UUID로 식별자를 생성한다. */
	public static AccountId newId() {
		return new AccountId(UUID.randomUUID());
	}
}
