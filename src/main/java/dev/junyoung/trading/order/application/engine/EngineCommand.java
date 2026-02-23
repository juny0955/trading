package dev.junyoung.trading.order.application.engine;

import java.util.concurrent.BlockingQueue;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.OrderStatus;
import dev.junyoung.trading.order.domain.model.value.OrderId;

/**
 * 매칭 엔진에 전달되는 커맨드 타입을 정의한다.
 *
 * <p>{@code sealed interface}로 선언되어 허용된 구현체({@link PlaceOrder}, {@link CancelOrder},
 * {@link Shutdown})만 존재한다. {@link EngineHandler}의 switch 패턴 매칭이 컴파일 타임에 완전성을 보장한다.</p>
 *
 * <p>모든 커맨드는 {@link EngineLoop}의 {@link BlockingQueue}를 통해
 * engine-thread로 전달되며, HTTP 스레드와의 직접 공유 없이 단일 스레드에서 순차 처리된다.</p>
 */
public sealed interface EngineCommand
		permits EngineCommand.PlaceOrder, EngineCommand.CancelOrder, EngineCommand.Shutdown {

	/**
	 * 지정가 주문 등록 커맨드.
	 * {@code order}는 {@link OrderStatus#ACCEPTED}
	 * 상태여야 하며, engine-thread에서 {@link MatchingEngine#placeLimitOrder}로 전달된다.
	 */
	record PlaceOrder(Order order) implements EngineCommand { }

	/**
	 * 주문 취소 커맨드.
	 * {@code orderId}에 해당하는 주문이 호가창에 없으면 {@link IllegalStateException}이 발생한다.
	 */
	record CancelOrder(OrderId orderId) implements EngineCommand { }

	/**
	 * 이벤트 루프 종료를 알리는 Poison Pill 커맨드.
	 * EngineLoop.stop()이 큐 마지막에 삽입하며,
	 * engine-thread가 수신하면 루프를 정상 종료한다.
	 */
	record Shutdown() implements EngineCommand { }
}
