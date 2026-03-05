package dev.junyoung.trading.order.domain.model.entity;

import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.common.exception.ConflictException;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * 주문 엔티티. 주문의 생명주기(ACCEPTED → NEW → FILLED/CANCELLED)를 관리한다.
 * <p>상태 전이는 {@link #activate()}, {@link #fill(Quantity)}, {@link #cancel()} 메서드를 통해서만 수행된다.</p>
 */
@Getter
public class Order {
    /** 주문 고유 식별자. 생성 시 자동 부여된다. */
    private final OrderId orderId;
    /** 매수({@link Side#BUY}) 또는 매도({@link Side#SELL}) */
    private final Side side;
    /** 주문 대상 종목 */
    private final Symbol symbol;
    /** 주문 유형: 지정가({@link OrderType#LIMIT}) 또는 시장가({@link OrderType#MARKET}) */
    private final OrderType orderType;
    /** 주문 유효 조건 (GTC / IOC / FOK). MARKET 주문은 내부적으로 IOC로 고정된다. */
    private final TimeInForce tif;

    @Getter(AccessLevel.NONE)
    private final Price price; // MARKET 주문은 null

    /** 주문 원래 수량 */
    private final Quantity quantity;
    /** 미체결 잔량. 체결될 때마다 차감된다. */
    private Quantity remaining;
    /** 현재 주문 상태. 엔진 스레드와 조회 스레드가 동시에 읽을 수 있어 volatile로 선언한다. */
    private volatile OrderStatus status;
    /** 주문 접수 시각 */
    private final Instant orderedAt;

    /**
     * 주문 객체를 생성한다. 외부에서는 {@link #createLimit} / {@link #createMarket}를 사용한다.
     *
     * @throws BusinessRuleException quantity가 1 미만인 경우
     */
    private Order(Side side, Symbol symbol, OrderType orderType, TimeInForce tif, Price price, Quantity quantity) {
        this.orderId = OrderId.newId();
        this.side = Objects.requireNonNull(side);
        this.symbol = Objects.requireNonNull(symbol);
        this.orderType = Objects.requireNonNull(orderType);
        this.tif = Objects.requireNonNull(tif);
        this.price = price;
        this.quantity = Objects.requireNonNull(quantity);

        if (quantity.value() < 1) {
            throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "quantity must be positive");
        }

        this.remaining = quantity;
        this.status = OrderStatus.ACCEPTED;
        this.orderedAt = Instant.now();
    }

    /**
     * 주문 유형에 따라 LIMIT 또는 MARKET 주문을 생성한다.
     * <p>tif가 null이면 {@link TimeInForce#GTC}로 기본 설정된다.</p>
     */
    public static Order create(Symbol symbol, Side side, OrderType orderType, TimeInForce tif, Price price, Quantity quantity) {
        return switch (orderType) {
            case MARKET -> Order.createMarket(side, symbol, quantity);
            case LIMIT  -> Order.createLimit(side, symbol, tif == null ? TimeInForce.GTC : tif, price, quantity);
        };
    }

    /**
     * LIMIT 주문을 생성한다.
     *
     * @throws NullPointerException price가 null인 경우
     */
    public static Order createLimit(Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        Objects.requireNonNull(price, "price must not be null for LIMIT order");
        return new Order(side, symbol, OrderType.LIMIT, tif, price, quantity);
    }

    /**
     * MARKET 주문을 생성한다. price는 항상 null이다. TIF는 내부적으로 IOC로 처리된다.
     */
    public static Order createMarket(Side side, Symbol symbol, Quantity quantity) {
        return new Order(side, symbol, OrderType.MARKET, TimeInForce.IOC, null, quantity);
    }

    /**
     * LIMIT 주문의 가격을 반환한다.
     *
     * @throws BusinessRuleException MARKET 주문에서 호출 시
     */
    public Price getLimitPriceOrThrow() {
        if (orderType.isMarket()) {
            throw new BusinessRuleException("ORDER_NO_PRICE", "MARKET order has no price");
        }
        return price;
    }

    /** {@code true}이면 시장가 주문({@link OrderType#MARKET})이다. */
    public boolean isMarket() {
        return orderType.isMarket();
    }

    /**
     * 매칭 엔진 진입 시 호출한다. 상태를 {@link OrderStatus#ACCEPTED}에서 {@link OrderStatus#NEW}로 전이한다.
     *
     * @throws ConflictException 상태가 {@link OrderStatus#ACCEPTED}가 아닌 경우
     */
    public void activate() {
        if (status != OrderStatus.ACCEPTED) {
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in accepted state: " + status);
        }
        this.status = OrderStatus.NEW;
    }

    /**
     * 체결 수량만큼 미체결 잔량을 차감하고 상태를 전이한다.
     * <ul>
     *   <li>잔량 > 0 → {@link OrderStatus#PARTIALLY_FILLED}</li>
     *   <li>잔량 = 0 → {@link OrderStatus#FILLED}</li>
     * </ul>
     *
     * @param executeQty 이번 체결 수량
     * @throws ConflictException 활성 상태({@link OrderStatus#NEW} / {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
     */
    public void fill(Quantity executeQty) {
        requireActive();
        this.remaining = remaining.sub(executeQty);
        this.status = remaining.value() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED;
    }

    /**
     * 주문을 취소한다. 상태를 {@link OrderStatus#CANCELLED}로 전이한다.
     *
     * @throws ConflictException 활성 상태({@link OrderStatus#NEW} / {@link OrderStatus#PARTIALLY_FILLED})가 아닌 경우
     */
    public void cancel() {
        requireActive();
        this.status = OrderStatus.CANCELLED;
    }

    /**
     * 상태가 활성({@link OrderStatus#NEW} / {@link OrderStatus#PARTIALLY_FILLED})인지 검증한다.
     *
     * @throws ConflictException 활성 상태가 아닌 경우
     */
    private void requireActive() {
        if (status != OrderStatus.NEW && status != OrderStatus.PARTIALLY_FILLED) {
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in an active state: " + status);
        }
    }
}
