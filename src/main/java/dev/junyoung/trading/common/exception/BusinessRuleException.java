package dev.junyoung.trading.common.exception;

import dev.junyoung.trading.common.exception.base.DomainException;

/**
 * 도메인 비즈니스 규칙 위반을 나타내는 예외. HTTP 422 Unprocessable Entity 로 매핑된다.
 * <p>예: 시장가 주문은 취소 불가 (타입 불변 규칙)</p>
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }
}
