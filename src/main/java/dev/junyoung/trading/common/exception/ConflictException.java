package dev.junyoung.trading.common.exception;

import dev.junyoung.trading.common.exception.base.DomainException;

/**
 * 도메인 상태 충돌을 나타내는 예외. HTTP 409 Conflict 로 매핑된다.
 * <p>예: 이미 완료된 주문 취소 시도</p>
 */
public class ConflictException extends DomainException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
