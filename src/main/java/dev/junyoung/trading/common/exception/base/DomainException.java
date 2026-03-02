package dev.junyoung.trading.common.exception.base;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
