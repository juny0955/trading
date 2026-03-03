package dev.junyoung.trading.common.exception;

import dev.junyoung.trading.common.exception.base.BusinessException;

/**
 * 리소스를 찾을 수 없을 때 사용하는 예외. HTTP 404 Not Found 로 매핑된다.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
