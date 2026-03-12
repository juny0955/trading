package dev.junyoung.trading.order.domain.model.entity;

import dev.junyoung.trading.account.domain.model.value.AccountId;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.common.exception.ConflictException;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.*;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 주문 애그리게이트 루트.
 * 주문 생명주기와 상태 전이를 관리한다.
 *
 * <pre>
 * ACCEPTED -> NEW -> PARTIALLY_FILLED -> FILLED
 * NEW / PARTIALLY_FILLED -> CANCELLED
 * </pre>
 *
 * 유일한 진입점은
 * {@link #create(AccountId, String, Symbol, Side, OrderType, TimeInForce, Price, QuoteQty, Quantity)}이다.
 */
@Getter
public class Order {

    private final OrderId orderId;
    private final AccountId accountId;
    private final String clientOrderId;
    private final Side side;
    private final Symbol symbol;
    private final OrderType orderType;
    private final TimeInForce tif;

    /** 시장가 주문에서는 null. */
    @Getter(AccessLevel.NONE)
    private final Price price;

    private final QuoteQty quoteQty;
    private final Quantity quantity;
    private final Instant orderedAt;

    private Quantity remaining;
    private OrderStatus status;
    private long cumQuoteQty;
    private long cumBaseQty;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private Order(
        OrderId orderId,
        AccountId accountId,
        String clientOrderId,
        Side side,
        Symbol symbol,
        OrderType orderType,
        TimeInForce tif,
        Price price,
        QuoteQty quoteQty,
        Quantity quantity,
        Instant orderedAt,
        Quantity remaining,
        OrderStatus status,
        long cumQuoteQty,
        long cumBaseQty
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.clientOrderId = Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        this.side = Objects.requireNonNull(side, "side must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        this.tif = Objects.requireNonNull(tif, "tif must not be null");
        this.price = price;
        this.quoteQty = quoteQty;
        this.quantity = quantity;
        this.orderedAt = Objects.requireNonNull(orderedAt, "orderedAt must not be null");
        this.remaining = Objects.requireNonNull(remaining, "remaining must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.cumQuoteQty = cumQuoteQty;
        this.cumBaseQty = cumBaseQty;

        validateAmounts();
    }

    private void validateAmounts() {
        if (quantity != null && quantity.value() < 1)
            throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "quantity must be positive");

        if (quoteQty != null && quoteQty.value() < 1)
            throw new BusinessRuleException("ORDER_INVALID_QUOTEQTY", "quoteQty must be over 1");
    }

    // -------------------------------------------------------------------------
    // 팩토리 (진입점: create)
    // -------------------------------------------------------------------------

    /**
     * 주문 유형과 사이드 규칙에 따라 주문을 생성한다.
     * LIMIT 주문에서 TIF가 null이면 {@link TimeInForce#defaultValue()}로 대체한다.
     */
    public static Order create(AccountId accountId, String clientOrderId, Symbol symbol, Side side, OrderType orderType,
            TimeInForce tif, Price price, QuoteQty quoteQty, Quantity quantity) {
        validateInputCombination(side, orderType, price, quoteQty, quantity);
        return switch (orderType) {
            case LIMIT -> createLimit(accountId, clientOrderId, side, symbol, tif != null ? tif : TimeInForce.defaultValue(), price, quantity);
            case MARKET -> side.isBuy()
                ? createMarketBuyWithQuoteQty(accountId, clientOrderId, side, symbol, quoteQty)
                : createMarketSell(accountId, clientOrderId, side, symbol, quantity);
        };
    }

    public static Order restore(OrderState state) {
        Objects.requireNonNull(state, "state must not be null");
        validateInputCombination(state.side(), state.orderType(), state.price(), state.quoteQty(), state.quantity());
        return new Order(
            state.orderId(),
            state.accountId(),
            state.clientOrderId(),
            state.side(),
            state.symbol(),
            state.orderType(),
            state.tif(),
            state.price(),
            state.quoteQty(),
            state.quantity(),
            state.orderedAt(),
            state.remaining(),
            state.status(),
            state.cumQuoteQty(),
            state.cumBaseQty()
        );
    }

    /**
     * create 진입점에서 입력 조합의 유효성을 검사한다.
     * 개별 값의 범위 검사는 {@link #validateAmounts()}에서 처리한다.
     */
    private static void validateInputCombination(Side side, OrderType orderType,
        Price price, QuoteQty quoteQty, Quantity quantity) {
        if (orderType == OrderType.LIMIT) {
            Objects.requireNonNull(price, "price must not be null for LIMIT order");
            Objects.requireNonNull(quantity, "quantity must not be null for LIMIT order");
            return;
        }

        // MARKET BUY: quoteQty 필수, quantity 불가.
        if (side.isBuy()) {
            if (quoteQty == null)
                throw new BusinessRuleException("ORDER_INVALID_QUOTEQTY", "MARKET BUY requires quoteQty");
            if (quantity != null)
                throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "MARKET BUY does not support quantity mode");
        }

        // MARKET SELL: quantity가 필수다.
        if (side.isSell() && quantity == null)
            throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "MARKET SELL requires quantity");
    }

    /** 지정가 주문을 생성한다. */
    private static Order createLimit(AccountId accountId, String clientOrderId, Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return new Order(
            OrderId.newId(),
            accountId,
            clientOrderId,
            side,
            symbol,
            OrderType.LIMIT,
            tif,
            price,
            null,
            quantity,
            Instant.now(),
            quantity,
            OrderStatus.ACCEPTED,
            0,
            0
        );
    }

    /** MARKET SELL 주문을 생성한다. TIF는 IOC로 고정된다. */
    private static Order createMarketSell(AccountId accountId, String clientOrderId, Side side, Symbol symbol, Quantity quantity) {
        return new Order(
            OrderId.newId(),
            accountId,
            clientOrderId,
            side,
            symbol,
            OrderType.MARKET,
            TimeInForce.IOC,
            null,
            null,
            quantity,
            Instant.now(),
            quantity,
            OrderStatus.ACCEPTED,
            0,
            0
        );
    }

    /**
     * quoteQty 기반 시장가 BUY 주문을 생성한다.
     * quantity는 null이며, 완료 처리는 {@link #markFilledByMarketBuy()}를 통해 이루어진다.
     */
    private static Order createMarketBuyWithQuoteQty(AccountId accountId, String clientOrderId, Side side, Symbol symbol, QuoteQty quoteQty) {
        return new Order(
            OrderId.newId(),
            accountId,
            clientOrderId,
            side,
            symbol,
            OrderType.MARKET,
            TimeInForce.IOC,
            null,
            quoteQty,
            null,
            Instant.now(),
            new Quantity(0),
            OrderStatus.ACCEPTED,
            0,
            0
        );
    }

    // -------------------------------------------------------------------------
    // 누적
    // -------------------------------------------------------------------------

    /** quoteQty 모드에서 체결된 quote/base 금액을 누적한다. */
    public void accumulate(long quoteAmt, long baseQty) {
        this.cumQuoteQty = Math.addExact(this.cumQuoteQty, quoteAmt);
        this.cumBaseQty = Math.addExact(this.cumBaseQty, baseQty);
    }

    // -------------------------------------------------------------------------
    // 조회
    // -------------------------------------------------------------------------

    public boolean isMarket() {
        return orderType.isMarket();
    }

    /** quoteQty 기반 시장가 BUY 모드이면 true를 반환한다. */
    public boolean isQuoteQtyMode() {
        return quoteQty != null && side.isBuy() && isMarket();
    }

    public Optional<Long> getQuantityValue() {
        return Optional.ofNullable(quantity).map(Quantity::value);
    }

    public Optional<Long> getPriceValue() {
        return Optional.ofNullable(price).map(Price::value);
    }

    /**
     * 지정가 주문의 가격을 반환한다.
     *
     * @throws BusinessRuleException 시장가 주문에서 호출된 경우
     */
    public Price getLimitPriceOrThrow() {
        if (isMarket())
            throw new BusinessRuleException("ORDER_NO_PRICE", "MARKET order has no price");

        return price;
    }

    // -------------------------------------------------------------------------
    // 상태 전이
    // -------------------------------------------------------------------------

    /**
     * 주문 상태를 ACCEPTED에서 NEW로 전환한다.
     *
     * @throws ConflictException 현재 상태가 ACCEPTED가 아닌 경우
     */
    public void activate() {
        if (status != OrderStatus.ACCEPTED)
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in accepted state: " + status);

        this.status = OrderStatus.NEW;
    }

    /**
     * 체결 수량을 적용하고 상태를 전이한다.
     * <ul>
     *   <li>remaining > 0 → PARTIALLY_FILLED</li>
     *   <li>remaining = 0 → FILLED</li>
     * </ul>
     *
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public void fill(Quantity executeQty) {
        requireActive();
        this.remaining = remaining.sub(executeQty);
        this.status = remaining.value() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED;
    }

    /**
     * quoteQty 시장가 BUY 주문을 remaining 변경 없이 FILLED로 완료한다.
     *
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public void markFilledByMarketBuy() {
        requireActive();
        this.status = OrderStatus.FILLED;
    }

    /**
     * 주문을 취소하고 상태를 CANCELLED로 전환한다.
     *
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public void cancel() {
        requireActive();
        this.status = OrderStatus.CANCELLED;
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private void requireActive() {
        if (status != OrderStatus.NEW && status != OrderStatus.PARTIALLY_FILLED)
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in an active state: " + status);
    }

    public record OrderState(
        OrderId orderId,
        AccountId accountId,
        String clientOrderId,
        Side side,
        Symbol symbol,
        OrderType orderType,
        TimeInForce tif,
        Price price,
        QuoteQty quoteQty,
        Quantity quantity,
        Instant orderedAt,
        Quantity remaining,
        OrderStatus status,
        long cumQuoteQty,
        long cumBaseQty
    ) {
    }
}
