package dev.junyoung.trading.order.domain.model.entity;

import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.enums.OrderType;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.enums.TimeInForce;
import dev.junyoung.trading.order.domain.model.value.OrderId;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.QuoteQty;
import dev.junyoung.trading.order.domain.model.value.Symbol;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import dev.junyoung.trading.common.exception.ConflictException;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * 주문 엔티티. 주문의 생명주기를 관리한다.
 *
 * <pre>
 * ACCEPTED → NEW → PARTIALLY_FILLED ┐
 *                                    ├→ FILLED
 *                 NEW ───────────────┘
 *                 NEW / PARTIALLY_FILLED → CANCELLED
 * </pre>
 *
 * 외부 진입점은 {@link #create}이며, 상태 전이는 {@link #activate()},
 * {@link #fill(Quantity)}, {@link #cancel()}을 통해서만 수행한다.
 */
@Getter
public class Order {

    private final OrderId     orderId;
    private final Side        side;
    private final Symbol      symbol;
    private final OrderType   orderType;
    private final TimeInForce tif;

    /** MARKET 주문은 null. 외부 접근은 {@link #getLimitPriceOrThrow()} 사용 */
    @Getter(AccessLevel.NONE)
    private final Price price;

    private final QuoteQty quoteQty;
    private final Quantity quantity;
    private final Instant  orderedAt;

    private volatile Quantity    remaining;
    private volatile OrderStatus status;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    private Order(Side side, Symbol symbol, OrderType orderType, TimeInForce tif,
        Price price, QuoteQty quoteQty, Quantity quantity) {

        // 필드 할당
        this.orderId   = OrderId.newId();
        this.side      = Objects.requireNonNull(side,      "side must not be null");
        this.symbol    = Objects.requireNonNull(symbol,    "symbol must not be null");
        this.orderType = Objects.requireNonNull(orderType, "orderType must not be null");
        this.tif       = Objects.requireNonNull(tif,       "tif must not be null");
        this.price     = price;
        this.quoteQty  = quoteQty;
        this.quantity  = quantity;
        this.remaining = quantity != null ? quantity : new Quantity(0);
        this.status    = OrderStatus.ACCEPTED;
        this.orderedAt = Instant.now();

        // 불변 조건 검증
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
     * 주문 유형에 맞는 Order를 생성한다. tif가 null이면 LIMIT 주문에 한해 GTC로 기본 설정된다.
     */
    public static Order create(Symbol symbol, Side side, OrderType orderType,
        TimeInForce tif, Price price, QuoteQty quoteQty, Quantity quantity) {
        validateInputCombination(side, orderType, price, quoteQty, quantity);
        return switch (orderType) {
            case LIMIT  -> createLimit(side, symbol, tif != null ? tif : TimeInForce.defaultValue(), price, quantity);
            case MARKET -> side.isBuy() && quoteQty != null ? createMarketBuyWithQuoteQty(side, symbol, quoteQty) : createMarket(side, symbol, quantity);
        };
    }

    /**
     * create 진입 시점의 입력값 조합 검증.
     * orderType / side 조합의 비즈니스 규칙을 검사한다.
     * 값 자체의 범위 검증({@link #validateAmounts()})과 역할이 다르다.
     */
    private static void validateInputCombination(Side side, OrderType orderType,
        Price price, QuoteQty quoteQty, Quantity quantity) {
        if (orderType == OrderType.LIMIT) {
            Objects.requireNonNull(price,    "price must not be null for LIMIT order");
            Objects.requireNonNull(quantity, "quantity must not be null for LIMIT order");
            return;
        }

        // MARKET BUY: quantity, quoteQty 중 정확히 하나만 허용
        if (side.isBuy()) {
            boolean hasQuantity = quantity != null;
            boolean hasQuoteQty = quoteQty != null;

            if (hasQuantity == hasQuoteQty)
                throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "MARKET BUY must specify exactly one of quantity or quoteQty");
        }

        // MARKET SELL: quantity 필수
        if (side.isSell() && quantity == null) {
            throw new BusinessRuleException("ORDER_INVALID_QUANTITY", "MARKET SELL requires quantity");
        }
    }

    /** LIMIT 주문 생성 */
    private static Order createLimit(Side side, Symbol symbol, TimeInForce tif, Price price, Quantity quantity) {
        return new Order(side, symbol, OrderType.LIMIT, tif, price, null, quantity);
    }

    /** MARKET 주문 생성 (quantity 기반). TIF는 IOC로 고정 */
    private static Order createMarket(Side side, Symbol symbol, Quantity quantity) {
        return new Order(side, symbol, OrderType.MARKET, TimeInForce.IOC, null, null, quantity);
    }

    /**
     * quoteQty(예산) 기반 MARKET BUY 주문 생성.
     * quantity는 null이며 체결 완료 시 {@link #markFilledByMarketBuy()}를 사용한다.
     */
    private static Order createMarketBuyWithQuoteQty(Side side, Symbol symbol, QuoteQty quoteQty) {
        return new Order(side, symbol, OrderType.MARKET, TimeInForce.IOC, null, quoteQty, null);
    }

    // -------------------------------------------------------------------------
    // 조회
    // -------------------------------------------------------------------------

    public boolean isMarket() {
        return orderType.isMarket();
    }

    /** quoteQty(예산) 기반 MARKET BUY 모드 여부 */
    public boolean isQuoteQtyMode() {
        return quoteQty != null;
    }

    /**
     * LIMIT 주문의 가격을 반환한다.
     *
     * @throws BusinessRuleException MARKET 주문에서 호출 시
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
     * 매칭 엔진 진입 시 호출한다. ACCEPTED → NEW
     *
     * @throws ConflictException 상태가 ACCEPTED가 아닌 경우
     */
    public void activate() {
        if (status != OrderStatus.ACCEPTED)
            throw new ConflictException("ORDER_INVALID_STATE", "Order is not in accepted state: " + status);

        this.status = OrderStatus.NEW;
    }

    /**
     * 체결 수량만큼 잔량을 차감하고 상태를 전이한다.
     * <ul>
     *   <li>잔량 > 0 → PARTIALLY_FILLED</li>
     *   <li>잔량 = 0 → FILLED</li>
     * </ul>
     *
     * @throws ConflictException 활성 상태(NEW / PARTIALLY_FILLED)가 아닌 경우
     */
    public void fill(Quantity executeQty) {
        requireActive();
        this.remaining = remaining.sub(executeQty);
        this.status    = remaining.value() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED;
    }

    /**
     * quoteQty 모드 MARKET BUY 체결 완료 시 호출한다. remaining은 변경하지 않는다.
     *
     * @throws ConflictException 활성 상태가 아닌 경우
     */
    public void markFilledByMarketBuy() {
        requireActive();
        this.status = OrderStatus.FILLED;
    }

    /**
     * 주문을 취소한다. → CANCELLED
     *
     * @throws ConflictException 활성 상태(NEW / PARTIALLY_FILLED)가 아닌 경우
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
}
