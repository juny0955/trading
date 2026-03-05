package dev.junyoung.trading.order.adapter.in.rest.validation;

import dev.junyoung.trading.order.adapter.in.rest.request.PlaceOrderRequest;
import dev.junyoung.trading.order.adapter.in.rest.validation.annotation.ValidPlaceOrder;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link PlaceOrderRequest} 클래스 레벨 교차 필드 검증기.
 *
 * <p>필드 단위 {@code @ValidEnum}으로 표현할 수 없는 orderType·side·tif 조합 규칙을 검증한다.
 * 검증 규칙은 아래와 같다.</p>
 *
 * <ul>
 *   <li>MARKET 주문에 {@code tif}를 명시하면 거부 (MARKET은 TIF 개념이 없음)</li>
 *   <li>MARKET BUY: {@code quantity} / {@code quoteQty} 중 정확히 하나만 입력 (XOR)</li>
 *   <li>MARKET SELL: {@code quantity} 필수</li>
 *   <li>LIMIT: {@code quantity} 필수</li>
 * </ul>
 */
public class PlaceOrderValidator implements ConstraintValidator<ValidPlaceOrder, PlaceOrderRequest> {

    private static final String MSG_TIF_NOT_ALLOWED_FOR_MARKET = "TIF is not allowed for MARKET orders";
    private static final String MSG_QTY_REQUIRED_FOR_LIMIT = "quantity is required for LIMIT orders";
    private static final String MSG_QTY_REQUIRED_FOR_SELL_MARKET = "quantity is required for SELL MARKET orders";
    private static final String MSG_BUY_MUST_HAVE_EXACTLY_ONE = "Either quantity or quoteQty must be specified for BUY orders";

    @Override
    public boolean isValid(PlaceOrderRequest request, ConstraintValidatorContext context) {
        boolean valid = true;

        boolean isMarket = OrderType.isMarket(request.orderType());
        boolean isBuy = Side.isBuy(request.side());

        // MARKET에 TIF 입력 금지
        if (isMarket && request.tif() != null) {
            valid &= addViolation(context, "tif", MSG_TIF_NOT_ALLOWED_FOR_MARKET);
        }

        if (OrderType.isMarket(request.orderType())) {
            valid &= validMarketOrder(request, context, isBuy);
        } else {
            valid &= validLimitOrder(request, context);
        }

        if (!valid) {
            context.disableDefaultConstraintViolation();
        }

        return valid;
    }

    /** MARKET 주문을 side에 따라 BUY / SELL 검증으로 위임한다. */
    private boolean validMarketOrder(PlaceOrderRequest request, ConstraintValidatorContext context, boolean isBuy) {
        return isBuy ? validMarketBuyOrder(request, context) : validMarketSellOrder(request, context);
    }

    /**
     * MARKET BUY는 quantity / quoteQty 중 정확히 하나만 입력해야 한다 (XOR).
     * <ul>
     *   <li>둘 다 null → 수량 기준을 알 수 없으므로 거부</li>
     *   <li>둘 다 입력 → 모호하므로 거부</li>
     * </ul>
     * 에러는 두 필드 모두에 부착하여 클라이언트가 어느 쪽이 문제인지 식별할 수 있게 한다.
     */
    private boolean validMarketBuyOrder(PlaceOrderRequest request, ConstraintValidatorContext context) {
        boolean hasQuantity = request.quantity() != null;
        boolean hasQuoteQty = request.quoteQty() != null;

        if (hasQuantity == hasQuoteQty) {
            addViolation(context, "quantity", MSG_BUY_MUST_HAVE_EXACTLY_ONE);
            addViolation(context, "quoteQty", MSG_BUY_MUST_HAVE_EXACTLY_ONE);
            return false;
        }

        return true;
    }

    /** MARKET SELL은 quantity 기반으로만 동작하므로 quantity가 필수다. */
    private boolean validMarketSellOrder(PlaceOrderRequest request, ConstraintValidatorContext context) {
        if (request.quantity() == null) {
            addViolation(context, "quantity", MSG_QTY_REQUIRED_FOR_SELL_MARKET);
            return false;
        }

        return true;
    }

    /** LIMIT 주문은 quantity가 필수다. */
    private boolean validLimitOrder(PlaceOrderRequest request, ConstraintValidatorContext context) {
        if (request.quantity() == null) {
            addViolation(context, "quantity", MSG_QTY_REQUIRED_FOR_LIMIT);
            return false;
        }

        return true;
    }

    private boolean addViolation(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(field)
            .addConstraintViolation();

        return false;
    }
}
