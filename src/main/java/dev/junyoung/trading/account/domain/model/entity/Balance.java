package dev.junyoung.trading.account.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import lombok.Getter;

/**
 * 자산 단위 잔고.
 *
 * <pre>
 * available + held = total
 * </pre>
 *
 * 외부 진입점은 {@link #of(Asset, long, long)} / {@link #zeroOf(Asset)}이며,
 * 주문 접수 시 hold 예약은 {@link #reserve(long)}로만 수행한다.
 */
@Getter
public class Balance {

    private final Asset asset;
    private final long available;
    private final long held;
    private final Instant createdAt;
    private final Instant updatedAt;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    Balance(Asset asset, long available, long held, Instant createdAt, Instant updatedAt) {
        this.asset = Objects.requireNonNull(asset, "asset must not be null");
        this.available = available;
        this.held = held;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        validateInvariants();
    }

    private void validateInvariants() {
        if (available < 0)
            throw new BusinessRuleException("BALANCE_INVALID_AVAILABLE", "available must be non-negative");

        if (held < 0)
            throw new BusinessRuleException("BALANCE_INVALID_HELD", "held must be non-negative");
    }

    // -------------------------------------------------------------------------
    // 팩토리 (진입점)
    // -------------------------------------------------------------------------

    /**
     * 지정한 자산과 잔고 값으로 Balance를 생성한다.
     *
     * @throws BusinessRuleException available 또는 held가 음수인 경우
     */
    public static Balance of(Asset asset, long available, long held) {
        Instant now = Instant.now();
        return new Balance(asset, available, held, now, now);
    }

    /** 0 잔고를 가진 Balance를 생성한다. */
    public static Balance zeroOf(Asset asset) {
        Instant now = Instant.now();
        return new Balance(asset, 0, 0, now, now);
    }

    /** 영속 계층에 저장된 Balance를 aggregate로 복원한다. */
    public static Balance restore(Asset asset, long available, long held, Instant createdAt, Instant updatedAt) {
        return new Balance(asset, available, held, createdAt, updatedAt);
    }

    // -------------------------------------------------------------------------
    // 조회
    // -------------------------------------------------------------------------

    /**
     * 총 잔고를 반환한다 ({@code available + held}).
     * DB에는 저장하지 않는 계산값이다.
     */
    public long total() {
        return Math.addExact(available, held);
    }

    // -------------------------------------------------------------------------
    // 상태 전이
    // -------------------------------------------------------------------------

    /**
     * 지정한 수량만큼 available을 줄이고 held를 증가시킨다.
     *
     * @throws BusinessRuleException hold 예약 수량이 0 이하이거나 사용 가능 잔고가 부족한 경우
     */
    public Balance reserve(long amount) {
        validateAmount(amount);
        requireSufficientAvailable(amount);

        return new Balance(
            asset,
            Math.subtractExact(available, amount),
            Math.addExact(held, amount),
            createdAt,
            Instant.now()
        );
    }

    /**
     * 지정한 수량만큼 held를 줄이고 available을 증가시킨다.
     *
     * @throws BusinessRuleException hold 해제 수량이 0 이하이거나 held 잔고가 부족한 경우
     */
    public Balance release(long amount) {
        validateAmount(amount);
        requireSufficientHeld(amount);

        return new Balance(
            asset,
            Math.addExact(available, amount),
            Math.subtractExact(held, amount),
            createdAt,
            Instant.now()
        );
    }

    public Balance applyDelta(long availableDelta, long heldDelta) {
        return new Balance(
            asset,
            Math.addExact(available, availableDelta),
            Math.addExact(held, heldDelta),
            createdAt,
            Instant.now()
        );
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    private void validateAmount(long amount) {
        if (amount <= 0)
            throw new BusinessRuleException("BALANCE_AMOUNT_INVALID", "amount must be positive");
    }

    private void requireSufficientAvailable(long amount) {
        if (available < amount)
            throw new BusinessRuleException("BALANCE_INSUFFICIENT", "insufficient available balance");
    }

    private void requireSufficientHeld(long amount) {
        if (held < amount)
            throw new BusinessRuleException("BALANCE_INSUFFICIENT", "insufficient held balance");
    }
}
