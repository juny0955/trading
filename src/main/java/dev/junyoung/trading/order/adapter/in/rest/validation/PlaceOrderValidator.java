package dev.junyoung.trading.order.adapter.in.rest.validation;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.validation.annotation.ValidPlaceOrder;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link PlaceOrderRequest} 클래스 레벨 교차 필드 검증기.
 *
 * <p>필드 단위 {@code @ValidEnum}으로 표현할 수 없는
 * orderType ↔ tif 조합 규칙을 검증한다.</p>
 *
 * <ul>
 *   <li>MARKET 주문에 {@code tif}를 명시하면 거부</li>
 * </ul>
 */
public class PlaceOrderValidator implements ConstraintValidator<ValidPlaceOrder, PlaceOrderRequest> {

    @Override
    public boolean isValid(PlaceOrderRequest request, ConstraintValidatorContext context) {
        // orderType 자체가 잘못된 값인 경우 @ValidEnum이 처리하므로 스킵
        if (request.tif() != null && OrderType.MARKET.name().equals(request.orderType())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("TIF is not allowed for MARKET orders")
                    .addPropertyNode("tif")
                    .addConstraintViolation();

            return false;
        }

        return true;
    }
}
