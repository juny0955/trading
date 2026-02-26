package dev.junyoung.trading.order.domain.model.entity;

import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class Order {
    private final OrderId orderId;
    private final Side side;
    private final Symbol symbol;
    private final OrderType orderType;

    @Getter(AccessLevel.NONE)
    private final Price price; // MARKET 주문은 null

    private final Quantity quantity;
    private volatile Quantity remaining;
    private volatile OrderStatus status;
    private final Instant orderedAt;

    private Order(Side side, Symbol symbol, OrderType orderType, Price price, Quantity quantity) {
        this.orderId = OrderId.newId();
        this.side = Objects.requireNonNull(side);
        this.symbol = Objects.requireNonNull(symbol);
        this.orderType = Objects.requireNonNull(orderType);
        this.price = price;
        this.quantity = Objects.requireNonNull(quantity);

        if (quantity.value() < 1) throw new IllegalArgumentException("quantity must be positive");

        this.remaining = quantity;
        this.status = OrderStatus.ACCEPTED;
        this.orderedAt = Instant.now();
    }

    /**
     * LIMIT 주문을 생성한다.
     *
     * @throws NullPointerException     price가 null인 경우
     */
    public static Order createLimit(Side side, Symbol symbol, Price price, Quantity quantity) {
        Objects.requireNonNull(price, "price must not be null for LIMIT order");
        return new Order(side, symbol, OrderType.LIMIT, price, quantity);
    }

    /**
     * MARKET 주문을 생성한다. price는 항상 null이다.
     */
    public static Order createMarket(Side side, Symbol symbol, Quantity quantity) {
        return new Order(side, symbol, OrderType.MARKET, null, quantity);
    }

    /**
     * LIMIT 주문의 가격을 반환한다.
     *
     * @throws IllegalStateException MARKET 주문에서 호출 시
     */
    public Price getLimitPriceOrThrow() {
        if (orderType.isMarket()) {
            throw new IllegalStateException("MARKET order has no price");
        }
        return price;
    }

    public boolean isMarket() {
        return orderType.isMarket();
    }

    /**
     * 매칭 엔진 진입 시 호출한다. 상태를 {@link OrderStatus#ACCEPTED}에서 {@link OrderStatus#NEW}로 전이한다.
     *
     * @throws IllegalStateException 상태가 {@link OrderStatus#ACCEPTED}가 아닌 경우
     */
    public void activate() {
        if (status != OrderStatus.ACCEPTED) {
            throw new IllegalStateException("Order is not in accepted state: " + status);
        }
        this.status = OrderStatus.NEW;
    }

    /**
     * 체결 수량만큼 미체결 잔량을 차감하고 상태를 전이한다.
     * <ul>
     *   <li>잔량 &gt; 0 → {@link OrderStatus#PARTIALLY_FILLED}</li>
     *   <li>잔량 = 0 → {@link OrderStatus#FILLED}</li>
     * </ul>
     *
     * @param executeQty 이번 체결 수량
     * @throws IllegalStateException 활성 상태({@link OrderStatus#NEW} / {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
     */
    public void fill(Quantity executeQty) {
        requireActive();
        this.remaining = remaining.sub(executeQty);
        this.status = remaining.value() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED;
    }

    /**
     * 주문을 취소한다. 상태를 {@link OrderStatus#CANCELLED}로 전이한다.
     *
     * @throws IllegalStateException 활성 상태({@link OrderStatus#NEW} / {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
     */
    public void cancel() {
        requireActive();
        this.status = OrderStatus.CANCELLED;
    }

    private void requireActive() {
        if (status != OrderStatus.NEW && status != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalStateException("Order is not in an active state: " + status);
        }
    }
}
