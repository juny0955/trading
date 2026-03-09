package dev.junyoung.trading.account.domain.model.entity;

import java.util.Objects;

import dev.junyoung.trading.account.domain.model.value.Asset;
import dev.junyoung.trading.common.exception.BusinessRuleException;
import lombok.Getter;

/**
 * 자산 단위 잔고.
 *
 * <p>잔고는 두 개의 구성 요소로 나뉜다.
 * <ul>
 *   <li>{@code available} — 실제 사용 가능한 자산 수량</li>
 *   <li>{@code held}      — 주문을 위해 예약된(잠긴) 자산 수량</li>
 * </ul>
 *
 * <p>{@code total}은 {@code available + held}의 계산값이며 DB에 저장하지 않는다.
 *
 * <p>외부 진입점은 {@link #of(Asset, long, long)}이다.
 */
@Getter
public class Balance {

	private final Asset asset;

	/** 실제 사용 가능한 자산 수량. 항상 0 이상이다. */
	private final long available;

	/** 주문 예약으로 잠긴 자산 수량. 항상 0 이상이다. */
	private final long held;

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	Balance(Asset asset, long available, long held) {
		this.asset = Objects.requireNonNull(asset, "asset must not be null");

		if (available < 0) throw new BusinessRuleException("BALANCE_INVALID_AVAILABLE", "available must be non-negative");
		if (held < 0) throw new BusinessRuleException("BALANCE_INVALID_HELD", "held must be non-negative");

		this.available = available;
		this.held = held;
	}

	// -------------------------------------------------------------------------
	// 팩토리 (진입점)
	// -------------------------------------------------------------------------

	/**
	 * 지정한 자산과 잔고 값으로 Balance를 생성한다.
	 * 영속 계층 복원 또는 초기 잔고 설정 시 사용한다.
	 *
	 * @throws BusinessRuleException available 또는 held 가 음수인 경우
	 */
	public static Balance of(Asset asset, long available, long held) {
		return new Balance(asset, available, held);
	}

	// -------------------------------------------------------------------------
	// 조회
	// -------------------------------------------------------------------------

	/**
	 * 총 잔고를 반환한다 ({@code available + held}).
	 * DB에 저장되지 않는 계산값이다.
	 */
	public long total() {
		return Math.addExact(available, held);
	}
}
