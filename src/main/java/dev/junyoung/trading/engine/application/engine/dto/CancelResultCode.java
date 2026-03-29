package dev.junyoung.trading.engine.application.engine.dto;

public enum CancelResultCode {
	ORDER_NOT_FOUND,
	ORDER_ALREADY_FINAL,
	OWNER_MISMATCH,
	SYMBOL_MISMATCH
}
