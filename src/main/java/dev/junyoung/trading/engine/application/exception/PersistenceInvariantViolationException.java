package dev.junyoung.trading.engine.application.exception;

public class PersistenceInvariantViolationException extends RuntimeException {
	public PersistenceInvariantViolationException(String message, Throwable cause) {
		super(message, cause);
	}
}
