package dev.junyoung.trading.common.exception;

import java.util.List;

import dev.junyoung.trading.common.exception.base.BusinessException;
import lombok.Getter;

/**
 * 입력 값 검증 실패를 나타내는 예외. HTTP 400 Bad Request 로 매핑된다.
 * <p>필드 단위 오류 목록을 {@link FieldError}로 담는다.</p>
 */
@Getter
public class ValidationException extends BusinessException {

    private final List<FieldError> fieldErrors;

    public ValidationException(ErrorCode errorCode, List<FieldError> fieldErrors) {
        super(errorCode);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

	public record FieldError(String field, String message, Object rejectedValue) { }
}
