package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.application.port.out.OrderRepository;
import dev.junyoung.trading.order.domain.model.OrderBook;
import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.entity.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link EngineCommand}를 수신해 {@link MatchingEngine}으로 디스패치하는 핸들러.
 *
 * <p>항상 engine-thread(단일 스레드)에서 호출된다. {@link EngineLoop}가 큐에서 커맨드를
 * 꺼내 이 클래스로 전달하므로, 내부 연산은 별도의 동기화 없이 안전하다.</p>
 *
 * <p>{@link EngineCommand}가 {@code sealed interface}이므로 switch 패턴 매칭이
 * 컴파일 타임에 완전성을 검사한다. 새 커맨드 타입 추가 시 여기에도 case를 추가해야 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EngineHandler {

	private final MatchingEngine engine;
	private final OrderBook orderBook;
	private final OrderBookCache orderBookCache;
	private final OrderRepository orderRepository;

	/**
	 * 커맨드 타입에 따라 엔진 동작을 실행한다.
	 *
	 * <ul>
	 *   <li>{@link EngineCommand.PlaceOrder}: 주문을 매칭 엔진에 전달하고 체결 결과를 저장한다.
	 *       taker/maker 상태 변경은 참조 공유로 OrderRepository에 자동 반영된다 (in-memory MVP).</li>
	 *   <li>{@link EngineCommand.CancelOrder}: 호가창에서 주문을 제거하고 상태를 CANCELLED로 전이 후 명시적 save.</li>
	 * </ul>
	 */
	public void handle(EngineCommand command) {
		switch (command) {
			case EngineCommand.PlaceOrder c -> {
				List<Trade> trades = engine.placeLimitOrder(c.order());
				if (!trades.isEmpty()) log.info("Trades executed: {}", trades);
				orderBookCache.update(orderBook);
			}
			case EngineCommand.CancelOrder c -> {
				Order cancelled = engine.cancelOrder(c.orderId());
				orderRepository.save(cancelled);
				orderBookCache.update(orderBook);
			}
			case EngineCommand.Shutdown _ ->
				// EngineLoop.run()이 직접 처리하므로 여기까지 오면 로직 오류
				log.warn("Shutdown command reached EngineHandler; this should not happen.");
		}
	}
}
