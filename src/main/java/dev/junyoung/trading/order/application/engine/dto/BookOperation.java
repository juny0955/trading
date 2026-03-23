package dev.junyoung.trading.order.application.engine.dto;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.value.OrderId;

/**
 * MatchingEngine 계산 결과로 생성되는 호가창 변경 명세.
 *
 * <ul>
 *   <li>{@link Add}    — resting order 신규 등록</li>
 *   <li>{@link Replace} — 동일 위치(FIFO / price level / side 유지) 상태 갱신</li>
 *   <li>{@link Remove}  — resting order 제거</li>
 * </ul>
 */
public sealed interface BookOperation permits BookOperation.Add, BookOperation.Replace, BookOperation.Remove {

    /**
     * resting order를 호가창에 신규 등록한다.
     *
     * <p>적용 대상: {@code LIMIT} 주문 + 활성 상태만 해당.
     * MARKET 주문은 resting book에 남지 않으므로 {@code Add} 대상이 아니다.</p>
     */
    record Add(Order order) implements BookOperation { }

    /**
     * 기존 resting order의 상태를 갱신한다.
     *
     * <p><b>불변 전제</b>: {@code orderId}, side, 가격 레벨, FIFO 위치가 모두 동일해야 한다.
     * price 변경 / amend / 재배치에는 사용하지 않는다.</p>
     */
    record Replace(Order updatedOrder) implements BookOperation { }

    /**
     * resting order를 호가창에서 제거한다.
     *
     * <p>체결 완료(fully filled) 또는 취소로 인해 book에서 삭제할 때 사용한다.</p>
     */
    record Remove(OrderId orderId) implements BookOperation { }
}
