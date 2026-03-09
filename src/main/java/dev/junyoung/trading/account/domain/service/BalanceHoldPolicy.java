package dev.junyoung.trading.account.domain.service;

import dev.junyoung.trading.account.domain.model.entity.Balance;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;

/**
 * 주문 유형별 hold 금액 계산 정책.
 *
 * <p>주문 접수 시 잠궈야 할 자산 수량을 주문 유형과 사이드에 따라 계산한다.
 * 거래소는 단일 정산 자산만 지원하며, 현재 정산 자산 코드는 {@code KRW}다.
 *
 * <pre>
 * LIMIT  BUY  → price × quantity  (settlement asset)
 * LIMIT  SELL → quantity          (symbol asset)
 * MARKET BUY  → quoteQty          (settlement asset)
 * MARKET SELL → quantity          (symbol asset)
 * </pre>
 *
 * <p>계산 결과는 {@link Balance}의 hold reserve에 전달되어 사용된다.
 */
public final class BalanceHoldPolicy {

    private static final Asset SETTLEMENT_ASSET = new Asset("KRW");

    private BalanceHoldPolicy() {}

    // -------------------------------------------------------------------------
    // 정책 계산
    // -------------------------------------------------------------------------

    /**
     * 주문 접수 시 reserve 해야 할 hold 자산과 금액을 계산한다.
     *
     * @param order ACCEPTED 상태의 주문
     * @return hold 자산 + 금액
     * @throws BusinessRuleException 지원하지 않는 주문 조합인 경우
     */
    public static HoldSpec holdSpecFor(Order order) {
        return switch (order.getOrderType()) {
            // 지정가 매수: price × quantity 만큼 KRW를 잠금
            // 지정가 매도: quantity 만큼 심볼 자산을 잠금
            case LIMIT -> order.getSide().isBuy()
                ? new HoldSpec(SETTLEMENT_ASSET, Math.multiplyExact(order.getLimitPriceOrThrow().value(), order.getQuantity().value()))
                : new HoldSpec(symbolAssetOf(order), order.getQuantity().value());
            // 시장가 매수: quoteQty(지불할 KRW 총액)를 잠금
            // 시장가 매도: quantity 만큼 심볼 자산을 잠금
            case MARKET -> order.getSide().isBuy()
                ? marketBuyHoldSpec(order)
                : new HoldSpec(symbolAssetOf(order), order.getQuantity().value());
        };
    }

    // 시장가 매수는 수량이 아닌 지불 금액(quoteQty) 기준으로 KRW를 잠근다.
    private static HoldSpec marketBuyHoldSpec(Order order) {
        return new HoldSpec(SETTLEMENT_ASSET, order.getQuoteQty().value());
    }

    // 주문의 심볼(예: BTC) 을 잠금 대상 자산으로 변환한다.
    private static Asset symbolAssetOf(Order order) {
        return new Asset(order.getSymbol().value());
    }

    public record HoldSpec(Asset asset, long amount) {
        public HoldSpec {
            if (amount <= 0)
                throw new BusinessRuleException("HOLD_AMOUNT_INVALID", "hold amount must be positive");
        }
    }
}
