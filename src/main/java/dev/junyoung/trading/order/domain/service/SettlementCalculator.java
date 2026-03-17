package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.service.dto.DeltaBook;
import dev.junyoung.trading.order.domain.service.dto.OrderIndex;
import dev.junyoung.trading.order.domain.service.dto.SettlementInput;
import dev.junyoung.trading.order.domain.service.dto.SettlementResult;

import java.util.List;

/**
 * 엔진 결과를 계정별 잔고 변화량({@link DeltaBook})으로 변환한다.
 *
 * <pre>
 * SettlementInput(trades, updatedOrders)
 *   → applyTrades  : 체결된 Trade마다 매수/매도 계정의 held·available 조정
 *   → applyRefund  : 최종 상태(FILLED/CANCELLED)인 주문의 미소진 hold 반환
 *   → SettlementResult
 * </pre>
 *
 * 역할은 계산에 한정되며, DB 반영은 하지 않는다.
 * 외부 진입점은 {@link #settle(SettlementInput)}이다.
 */
public final class SettlementCalculator {

    private static final Asset SETTLEMENT_ASSET = new Asset("KRW");

    // -------------------------------------------------------------------------
    // 진입점
    // -------------------------------------------------------------------------

    /**
     * 엔진 결과를 정산하여 계정별 잔고 변화량을 반환한다.
     *
     * @param input 체결된 Trade 목록과 상태가 갱신된 주문 목록
     * @return 계정·자산별 available/held 변화량
     */
    public static SettlementResult settle(SettlementInput input) {
       OrderIndex orderIndex = OrderIndex.of(input.updatedOrders());
       DeltaBook deltaBook = new DeltaBook();

       applyTrades(input.trades(), orderIndex, deltaBook);
       applyRefund(input.updatedOrders(), deltaBook);

       return SettlementResult.ofDeltaBook(deltaBook);
    }

    // -------------------------------------------------------------------------
    // 체결 반영
    // -------------------------------------------------------------------------

    /** Trade 목록을 순회하며 매수·매도 계정의 잔고 변화량을 기록한다. */
    private static void applyTrades(List<Trade> trades, OrderIndex orderIndex, DeltaBook deltaBook) {
        for (Trade trade : trades) {
            Quantity executedQty = trade.quantity();
            QuoteQty executedQuote = QuoteQty.ofPriceAndQuantity(trade.price(), executedQty);

            Order buyOrder = orderIndex.findOrder(trade.buyOrderId());
            Order sellOrder = orderIndex.findOrder(trade.sellOrderId());

            applyBuySettlement(buyOrder, executedQty, executedQuote, deltaBook);
            applySellSettlement(sellOrder, executedQty, executedQuote, deltaBook);
        }
    }

    /**
     * 매수 체결 반영: KRW held 차감, 기초자산 available 증가.
     *
     * <pre>
     * held(KRW)      -= executedQuote
     * available(base) += executedQty
     * </pre>
     */
    private static void applyBuySettlement(Order buyOrder, Quantity executedQty, QuoteQty executedQuote, DeltaBook deltaBook) {
        Asset baseAsset = Asset.of(buyOrder.getSymbol().value());

        deltaBook.subHeld(buyOrder.getAccountId(), SETTLEMENT_ASSET, executedQuote.value());
        deltaBook.addAvailable(buyOrder.getAccountId(), baseAsset, executedQty.value());
    }

    /**
     * 매도 체결 반영: 기초자산 held 차감, KRW available 증가.
     *
     * <pre>
     * held(base)     -= executedQty
     * available(KRW) += executedQuote
     * </pre>
     */
    private static void applySellSettlement(Order sellOrder, Quantity executedQty, QuoteQty executedQuote, DeltaBook deltaBook) {
        Asset baseAsset = Asset.of(sellOrder.getSymbol().value());

        deltaBook.subHeld(sellOrder.getAccountId(), baseAsset, executedQty.value());
        deltaBook.addAvailable(sellOrder.getAccountId(), SETTLEMENT_ASSET, executedQuote.value());
    }

    // -------------------------------------------------------------------------
    // 미소진 hold 환불
    // -------------------------------------------------------------------------

    /**
     * 최종 상태(FILLED/CANCELLED)인 주문의 남은 hold를 available로 환원한다.
     *
     * <p>부분 체결 후 취소된 주문이나 FOK 미체결 주문처럼 원래 hold보다
     * 적게 소진된 경우에 잔여분을 돌려준다.
     */
    private static void applyRefund(List<Order> orders, DeltaBook deltaBook) {
        for (Order order : orders) {
            if (!order.isFinal()) continue;

            long refundAmount = remainingHold(order);
            if (refundAmount == 0L) continue;

            Asset asset = BalanceHoldPolicy.holdSpecFor(order).asset();
            deltaBook.refund(order.getAccountId(), asset, refundAmount);
        }
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    /**
     * 주문의 원래 hold 금액에서 실제 소진량을 뺀 잔여 hold를 반환한다.
     *
     * <ul>
     *   <li>매수: originalHold(KRW) − cumQuoteQty</li>
     *   <li>매도: originalHold(base) − cumBaseQty</li>
     * </ul>
     */
    private static long remainingHold(Order order) {
        long originalHold = BalanceHoldPolicy.holdSpecFor(order).amount();
        long consumedHold = order.getSide().isBuy() ? order.getCumQuoteQty().value() : order.getCumBaseQty().value();

        return Math.subtractExact(originalHold, consumedHold);
    }
}
