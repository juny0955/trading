package dev.junyoung.trading.engine.application.exception;

public class RetryablePersistenceException extends RuntimeException {
	public RetryablePersistenceException(String message, Throwable cause) {
		super(message, cause);
	}
}
