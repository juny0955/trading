package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.account.domain.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;

/**
 * 주문 유형별 hold 금액 계산 정책.
 *
 * <p>주문 접수 시 잠궈야 할 자산 수량을 주문 유형과 사이드에 따라 계산한다.
 *
 * <pre>
 * LIMIT  BUY  → price × quantity  (quote asset)
 * LIMIT  SELL → quantity           (base  asset)
 * MARKET BUY  → quoteQty           (quote asset)
 * MARKET SELL → quantity           (base  asset)
 * </pre>
 *
 * <p>계산 결과는 {@link dev.junyoung.trading.account.domain.model.Balance}의
 * hold reserve에 전달되어 사용된다.
 */
public final class HoldPolicy {

    private HoldPolicy() {}

    // -------------------------------------------------------------------------
    // 정책 계산
    // -------------------------------------------------------------------------

    /**
     * 주문 접수 시 reserve 해야 할 hold 자산과 금액을 계산한다.
     *
     * @param order ACCEPTED 상태의 주문
     * @return hold 자산 + 금액
     * @throws BusinessRuleException 지원하지 않는 주문 조합이거나 심볼 형식이 잘못된 경우
     */
    public static HoldSpec holdSpecFor(Order order) {
        Asset baseAsset = baseAssetOf(order);
        Asset quoteAsset = quoteAssetOf(order);

        return switch (order.getOrderType()) {
            case LIMIT -> order.getSide().isBuy()
                ? new HoldSpec(quoteAsset, Math.multiplyExact(order.getLimitPriceOrThrow().value(), order.getQuantity().value()))
                : new HoldSpec(baseAsset, order.getQuantity().value());
            case MARKET -> order.getSide().isBuy()
                ? marketBuyHoldSpec(order, quoteAsset)
                : new HoldSpec(baseAsset, order.getQuantity().value());
        };
    }

    private static HoldSpec marketBuyHoldSpec(Order order, Asset quoteAsset) {
        if (order.getQuoteQty() == null) {
            throw new BusinessRuleException(
                "ORDER_UNSUPPORTED_MARKET_BUY_QUANTITY_HOLD",
                "MARKET BUY quantity mode is not supported for hold calculation"
            );
        }

        return new HoldSpec(quoteAsset, order.getQuoteQty().value());
    }

    private static Asset baseAssetOf(Order order) {
        return new Asset(splitSymbol(order)[0]);
    }

    private static Asset quoteAssetOf(Order order) {
        return new Asset(splitSymbol(order)[1]);
    }

    private static String[] splitSymbol(Order order) {
        String[] parts = order.getSymbol().value().split("-");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BusinessRuleException(
                "SYMBOL_INVALID_FORMAT",
                "symbol must be in BASE-QUOTE format"
            );
        }

        return parts;
    }

    public record HoldSpec(Asset asset, long amount) {
        public HoldSpec {
            if (amount <= 0) {
                throw new BusinessRuleException("HOLD_AMOUNT_INVALID", "hold amount must be positive");
            }
        }
    }
}
