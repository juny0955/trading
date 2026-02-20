package dev.junyoung.trading.order.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
public class Order {
    private final OrderId orderId;
    private final Side side;
    private final Price price;
    private final Quantity quantity;
    private Quantity remaining;
    private OrderStatus status;
    private final Instant orderedAt;

    /**
     * 주문을 생성한다. 초기 상태는 {@link OrderStatus#ACCEPTED}이다.
     *
     * @param side     매수/매도
     * @param price    지정가 (≥ 1)
     * @param quantity 주문 수량 (≥ 1)
     * @throws IllegalArgumentException quantity &lt; 1인 경우
     */
    public Order(Side side, Price price, Quantity quantity) {
        this.orderId = OrderId.newId();
        this.side = Objects.requireNonNull(side);
        this.price = Objects.requireNonNull(price);
        this.quantity = Objects.requireNonNull(quantity);

        if (quantity.value() < 1) throw new IllegalArgumentException("quantity must be positive");

        this.remaining = quantity;
        this.status = OrderStatus.ACCEPTED;
        this.orderedAt = Instant.now();
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
