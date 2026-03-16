package dev.junyoung.trading.account.application.exception.balance;

import org.springframework.http.HttpStatus;

import dev.junyoung.trading.common.exception.ErrorCode;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum BalanceErrorCode implements ErrorCode {
	BALANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "BALANCE_NOT_FOUND", "Account not found"),
	;

	private final HttpStatus status;
	private final String code;
	private final String message;

	@Override public HttpStatus status() { return status; }
	@Override public String code() { return code; }
	@Override public String message() { return message; }
}
