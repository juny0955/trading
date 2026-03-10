package dev.junyoung.trading.order.domain.model.value;

import dev.junyoung.trading.common.exception.BusinessRuleException;

import java.util.Objects;
import java.util.UUID;

/**
 * 주문을 식별하는 UUID 기반 식별자.
 *
 * 외부 진입점은 {@link #newId()} 및 {@link #from(String)}이다.
 */
public record OrderId(
    UUID value
) {

    public OrderId {
        Objects.requireNonNull(value, "value");
    }

    /** 새로운 무작위 UUID로 식별자를 생성한다. */
    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * UUID 문자열로부터 식별자를 생성한다.
     *
     * @throws dev.junyoung.trading.common.exception.BusinessRuleException raw가 null·공백이거나 UUID 형식이 아닌 경우
     */
    public static OrderId from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("ORDER_ID_INVALID", "OrderId cannot be null or blank");
        }

        try {
            return new OrderId(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("ORDER_ID_INVALID", "Invalid OrderId format");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
