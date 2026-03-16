package dev.junyoung.trading.order.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.service.dto.SettlementInput;
import dev.junyoung.trading.order.domain.service.dto.SettlementResult;

/**
 * 엔진 결과를 계정별 잔고 변화량으로 변환한다.
 *
 * <p>역할은 계산에 한정되며, DB 반영은 하지 않는다.
 */
public class SettlementCalculator {

    private static final Asset SETTLEMENT_ASSET = new Asset("KRW");

    public SettlementResult settle(SettlementInput input) {
        Map<OrderId, Order> orderIndex = indexOrders(input.updatedOrders());
        Map<BalanceKey, DeltaAccumulator> deltas = new LinkedHashMap<>();

        applyTradeDeltas(input.trades(), orderIndex, deltas);
        applyRefundDeltas(input.updatedOrders(), deltas);

        return SettlementResult.of(toBalanceDeltas(deltas));
    }

    private Map<OrderId, Order> indexOrders(List<Order> updatedOrders) {
        Map<OrderId, Order> orderIndex = new LinkedHashMap<>();
        for (Order order : updatedOrders)
            orderIndex.put(order.getOrderId(), order);

        return orderIndex;
    }

    private void applyTradeDeltas(List<Trade> trades, Map<OrderId, Order> orderIndex, Map<BalanceKey, DeltaAccumulator> deltas) {
        for (Trade trade : trades) {
            long executedQty = trade.quantity().value();
            long executedQuote = Math.multiplyExact(trade.price().value(), executedQty);

            Order buyOrder = findOrder(orderIndex, trade.buyOrderId());
            Order sellOrder = findOrder(orderIndex, trade.sellOrderId());

            // 구매 Order
            subDelta(deltas, buyOrder.getAccountId(), SETTLEMENT_ASSET, executedQuote);
            addDelta(deltas, buyOrder.getAccountId(), Asset.of(buyOrder.getSymbol().value()), executedQty, 0L);

            // 판매 Order
            subDelta(deltas, sellOrder.getAccountId(), Asset.of(sellOrder.getSymbol().value()), executedQty);
            addDelta(deltas, sellOrder.getAccountId(), SETTLEMENT_ASSET, executedQuote, 0L);
        }
    }

    private void applyRefundDeltas(List<Order> updatedOrders, Map<BalanceKey, DeltaAccumulator> deltas) {
        for (Order order : updatedOrders) {
            if (!order.getStatus().isFinal())
                continue;

            long refundAmount = remainingHold(order);
            if (refundAmount == 0L)
                continue;

            Asset holdAsset = new Asset(order.getSymbol().value());
            addDelta(deltas, order.getAccountId(), holdAsset, refundAmount, -refundAmount);
        }
    }

    private Order findOrder(Map<OrderId, Order> orderIndex, OrderId orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null)
            throw new BusinessRuleException("SETTLEMENT_ORDER_NOT_FOUND", "updatedOrders missing trade order: " + orderId);

        return order;
    }

    private long remainingHold(Order order) {
        long originalHold = BalanceHoldPolicy.holdSpecFor(order).amount();
        long consumedHold = order.getSide().isBuy()
            ? order.getCumQuoteQty().value()
            : order.getCumBaseQty().value();

        return Math.subtractExact(originalHold, consumedHold);
    }

    private void addDelta(Map<BalanceKey, DeltaAccumulator> deltas, AccountId accountId, Asset asset, long availableDelta, long heldDelta) {
        BalanceKey key = new BalanceKey(accountId, asset);
        deltas.computeIfAbsent(key, ignored -> new DeltaAccumulator())
            .add(availableDelta, heldDelta);
    }

    private void subDelta(Map<BalanceKey, DeltaAccumulator> deltas, AccountId accountId, Asset asset, long heldDelta) {
        BalanceKey key = new BalanceKey(accountId, asset);
        deltas.computeIfAbsent(key, ignored -> new DeltaAccumulator())
            .sub(heldDelta);
    }

    private List<SettlementResult.BalanceDelta> toBalanceDeltas(Map<BalanceKey, DeltaAccumulator> deltas) {
        List<SettlementResult.BalanceDelta> result = new ArrayList<>();
        for (Map.Entry<BalanceKey, DeltaAccumulator> entry : deltas.entrySet()) {
            DeltaAccumulator delta = entry.getValue();
            if (delta.availableDelta == 0L && delta.heldDelta == 0L)
                continue;

            result.add(new SettlementResult.BalanceDelta(
                entry.getKey().accountId,
                entry.getKey().asset,
                delta.availableDelta,
                delta.heldDelta
            ));
        }
        return result;
    }

    private record BalanceKey(AccountId accountId, Asset asset) { }

    private static final class DeltaAccumulator {
        private long availableDelta;
        private long heldDelta;

        private void add(long availableDelta, long heldDelta) {
            this.availableDelta = Math.addExact(this.availableDelta, availableDelta);
            this.heldDelta = Math.addExact(this.heldDelta, heldDelta);
        }

        private void sub(long heldDelta) {
            this.heldDelta = Math.subtractExact(this.heldDelta, heldDelta);
        }
    }
}
