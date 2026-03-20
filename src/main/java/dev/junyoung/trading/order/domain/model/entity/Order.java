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
import lombok.Builder;
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
 * {@link #create(OrderId, AccountId, String, long, Symbol, Side, OrderType, TimeInForce, Price, QuoteQty, Quantity)}이다.
 */
@Getter
@Builder(toBuilder = true)
public class Order {

    private final OrderId orderId;
    private final AccountId accountId;
    private final String clientOrderId;
    private final long acceptedSeq;
    private final Side side;
    private final Symbol symbol;
    private final OrderType orderType;
    private final TimeInForce tif;

    /** 시장가 주문에서는 null. */
    @Getter(AccessLevel.NONE)
    private final Price price;

    private final QuoteQty quoteQty;
    private final Quantity quantity;

    private final Quantity remaining;
    private final OrderStatus status;
    private final QuoteQty cumQuoteQty;
    private final Quantity cumBaseQty;

    private final Instant orderedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private Order(
        OrderId orderId,
        AccountId accountId,
        String clientOrderId,
        long acceptedSeq,
        Side side,
        Symbol symbol,
        OrderType orderType,
        TimeInForce tif,
        Price price,
        QuoteQty quoteQty,
        Quantity quantity,
        Quantity remaining,
        OrderStatus status,
        QuoteQty cumQuoteQty,
        Quantity cumBaseQty,
        Instant orderedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.clientOrderId = Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        this.acceptedSeq = acceptedSeq;
        this.side = Objects.requireNonNull(side, "side must not be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        this.tif = Objects.requireNonNull(tif, "tif must not be null");
        this.price = price;
        this.quoteQty = quoteQty;
        this.quantity = quantity;
        this.remaining = Objects.requireNonNull(remaining, "remaining must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.cumQuoteQty = Objects.requireNonNull(cumQuoteQty, "cumQuoteQty must not be null");
        this.cumBaseQty = Objects.requireNonNull(cumBaseQty, "cumBaseQty must not be null");
        this.orderedAt = Objects.requireNonNull(orderedAt, "orderedAt must not be null");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

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
    public static Order create(OrderId orderId, AccountId accountId, String clientOrderId, long acceptedSeq,
        Symbol symbol, Side side, OrderType orderType,
        TimeInForce tif, Price price, QuoteQty quoteQty, Quantity quantity) {
        validateInputCombination(side, orderType, price, quoteQty, quantity);
        return switch (orderType) {
            case LIMIT -> createLimit(orderId, accountId, clientOrderId, acceptedSeq, side, symbol, tif != null ? tif : TimeInForce.defaultValue(), price, quantity);
            case MARKET -> side.isBuy()
                ? createMarketBuyWithQuoteQty(orderId, accountId, clientOrderId, acceptedSeq, side, symbol, quoteQty)
                : createMarketSell(orderId, accountId, clientOrderId, acceptedSeq, side, symbol, quantity);
        };
    }

    /** 저장소에 보관된 주문 상태를 aggregate로 복원한다. */
    public static Order restore(
        OrderId orderId,
        AccountId accountId,
        String clientOrderId,
        long acceptedSeq,
        Side side,
        Symbol symbol,
        OrderType orderType,
        TimeInForce tif,
        Price price,
        QuoteQty quoteQty,
        Quantity quantity,
        Quantity remaining,
        OrderStatus status,
        QuoteQty cumQuoteQty,
        Quantity cumBaseQty,
        Instant orderedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        validateInputCombination(side, orderType, price, quoteQty, quantity);
        return new Order(
            orderId,
            accountId,
            clientOrderId,
            acceptedSeq,
            side,
            symbol,
            orderType,
            tif,
            price,
            quoteQty,
            quantity,
            remaining,
            status,
            cumQuoteQty,
            cumBaseQty,
            orderedAt,
            createdAt,
            updatedAt
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
    private static Order createLimit(OrderId orderId, AccountId accountId, String clientOrderId, long acceptedSeq,
        Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return Order.builder()
            .orderId(orderId)
            .accountId(accountId)
            .clientOrderId(clientOrderId)
            .acceptedSeq(acceptedSeq)
            .side(side)
            .symbol(symbol)
            .orderType(OrderType.LIMIT)
            .tif(tif)
            .price(price)
            .quantity(quantity)
            .remaining(quantity)
            .status(OrderStatus.ACCEPTED)
            .cumBaseQty(Quantity.zero())
            .cumQuoteQty(QuoteQty.zero())
            .orderedAt(Instant.now())
            .build();
    }

    /** MARKET SELL 주문을 생성한다. TIF는 IOC로 고정된다. */
    private static Order createMarketSell(OrderId orderId, AccountId accountId, String clientOrderId, long acceptedSeq,
        Side side, Symbol symbol, Quantity quantity) {

        return Order.builder()
            .orderId(orderId)
            .accountId(accountId)
            .clientOrderId(clientOrderId)
            .acceptedSeq(acceptedSeq)
            .side(side)
            .symbol(symbol)
            .orderType(OrderType.MARKET)
            .tif(TimeInForce.IOC)
            .quantity(quantity)
            .remaining(quantity)
            .status(OrderStatus.ACCEPTED)
            .cumBaseQty(Quantity.zero())
            .cumQuoteQty(QuoteQty.zero())
            .orderedAt(Instant.now())
            .build();
    }

    /**
     * quoteQty 기반 시장가 BUY 주문을 생성한다.
     * quantity는 null이며, 완료 처리는 {@link #markFilledByMarketBuy()}를 통해 이루어진다.
     */
    private static Order createMarketBuyWithQuoteQty(OrderId orderId, AccountId accountId, String clientOrderId, long acceptedSeq,
        Side side, Symbol symbol, QuoteQty quoteQty) {

        return Order.builder()
            .orderId(orderId)
            .accountId(accountId)
            .clientOrderId(clientOrderId)
            .acceptedSeq(acceptedSeq)
            .side(side)
            .symbol(symbol)
            .orderType(OrderType.MARKET)
            .tif(TimeInForce.IOC)
            .quoteQty(quoteQty)
            .remaining(new Quantity(0))
            .status(OrderStatus.ACCEPTED)
            .cumBaseQty(Quantity.zero())
            .cumQuoteQty(QuoteQty.zero())
            .orderedAt(Instant.now())
            .build();
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

    public boolean isFinal() { return status.isFinal(); }

    public boolean isActive() { return status.isActive(); }

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
    public Order activate() {
        if (status != OrderStatus.ACCEPTED)
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in accepted state: " + status);

        return this.toBuilder()
            .status(OrderStatus.NEW)
            .build();
    }

    /**
     * 체결 수량과 체결가를 적용하고 상태를 전이한다.
     *
     * <p>체결가는 항상 maker의 지정가를 사용한다.
     * BUY 주문의 경우 지정가보다 낮은 maker 가격에 체결되면 가격 개선이 발생하며,
     * 이 때 {@code cumQuoteQty < limitPrice × quantity}가 되어 정산 시 차액이 hold에서 반환된다.
     *
     * <ul>
     *   <li>remaining > 0 → PARTIALLY_FILLED</li>
     *   <li>remaining = 0 → FILLED</li>
     * </ul>
     *
     * @param executedQty   체결 수량
     * @param executedPrice 체결가 (maker의 지정가)
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public Order fill(Quantity executedQty, Price executedPrice) {
        requireActive();

        long executedBase = executedQty.value();
        long executedQuote = Math.multiplyExact(executedPrice.value(), executedBase);

        Quantity afterRemaining = remaining.sub(executedQty);
        return this.toBuilder()
            .cumBaseQty(cumBaseQty.add(executedBase))
            .cumQuoteQty(cumQuoteQty.add(executedQuote))
            .remaining(afterRemaining)
            .status(afterRemaining.isPositive() ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED)
            .build();
    }

    /**
     * quoteQty 예산 기반 MARKET BUY 주문의 체결 누적을 처리한다.
     *
     * <p>{@link #fill(Quantity, Price)}와 달리 {@code remaining}을 갱신하지 않는다.
     * MARKET BUY는 수량 목표가 아닌 예산({@code quoteQty}) 기반으로 동작하므로
     * remaining은 의미 없는 값(초기값 0)으로 고정되며, 종료 상태 전이는
     * {@link #markFilledByMarketBuy()} 또는 {@link #cancel()}이 담당한다.
     *
     * @param executedQty   체결 수량 (base asset)
     * @param executedPrice 체결가 (maker의 지정가)
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public Order fillQuoteMode(Quantity executedQty, Price executedPrice) {
        requireActive();

        long executedBase = executedQty.value();
        long executedQuote = Math.multiplyExact(executedPrice.value(), executedBase);

        return this.toBuilder()
            .cumBaseQty(cumBaseQty.add(executedBase))
            .cumQuoteQty(cumQuoteQty.add(executedQuote))
            .build();
    }

    /**
     * quoteQty 시장가 BUY 주문을 remaining 변경 없이 FILLED로 완료한다.
     *
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public Order markFilledByMarketBuy() {
        requireActive();

        return this.toBuilder()
            .status(OrderStatus.FILLED)
            .build();
    }

    /**
     * 주문을 취소하고 상태를 CANCELLED로 전환한다.
     *
     * @throws ConflictException 현재 상태가 활성 상태가 아닌 경우
     */
    public Order cancel() {
        requireActive();

        return this.toBuilder()
            .status(OrderStatus.CANCELLED)
            .build();
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private void requireActive() {
        if (status != OrderStatus.NEW && status != OrderStatus.PARTIALLY_FILLED)
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in an active state: " + status);
    }
}
