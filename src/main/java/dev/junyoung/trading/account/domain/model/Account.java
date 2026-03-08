package dev.junyoung.trading.account.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import dev.junyoung.trading.account.domain.value.AccountId;
import dev.junyoung.trading.account.domain.value.Asset;
import lombok.Getter;

/**
 * 거래의 소유 주체 계정.
 *
 * <p>Account는 {@link AccountId}로 식별되며,
 * 자산별 잔고({@link Balance})를 {@link Asset} 키로 관리한다.
 *
 * <p>Lock 규칙:
 * <ul>
 *   <li>주문 접수 단계: quote 또는 base 중 단일 asset 행만 잠근다.</li>
 *   <li>정산 단계: 복수 asset 행을 잠글 경우 asset 이름 알파벳 오름차순으로
 *       획득한다 (예: BTC → KRW). 교차 lock으로 인한 데드락을 방지한다.</li>
 * </ul>
 */
@Getter
public class Account {

	private final AccountId accountId;

	/** 자산별 잔고 맵. 불변 복사본으로 보관된다. */
	private final Map<Asset, Balance> balances;

	private final Instant createdAt;

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	/**
	 * 계정을 생성한다.
	 *
	 * @param balances null 이면 빈 맵으로 초기화된다.
	 */
	public Account(AccountId accountId, Map<Asset, Balance> balances, Instant createdAt) {
		this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
		this.balances = balances == null ? Map.of() : Map.copyOf(balances);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}
}
