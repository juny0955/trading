package dev.junyoung.trading.order.domain.service;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;

import java.util.List;

/**
 * 주문 처리(place) 결과를 담는 값 객체.
 *
 * <ul>
 *   <li>{@code updatedOrders} — 상태가 변경된 주문 목록 (taker + 체결에 참여한 모든 maker). 영속화 대상.</li>
 *   <li>{@code trades} — 이번 처리에서 발생한 체결 내역.</li>
 * </ul>
 */
public record PlaceResult(List<Order> updatedOrders, List<Trade> trades) {

	public PlaceResult {
		updatedOrders = List.copyOf(updatedOrders);
		trades = List.copyOf(trades);
	}

	// -------------------------------------------------------------------------
	// 팩토리 (진입점)
	// -------------------------------------------------------------------------

	/** 상태가 변경된 주문 목록과 체결 내역으로 결과를 생성한다. */
	public static PlaceResult of(List<Order> updatedOrders, List<Trade> trades) {
		return new PlaceResult(updatedOrders, trades);
	}
}
