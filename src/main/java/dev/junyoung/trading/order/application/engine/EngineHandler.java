package dev.junyoung.trading.order.application.engine;

import dev.junyoung.trading.order.domain.model.OrderBook;
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

	/**
	 * 커맨드 타입에 따라 엔진 동작을 실행한다.
	 *
	 * <ul>
	 *   <li>{@link EngineCommand.PlaceOrder}: 주문을 매칭 엔진에 전달하고 체결 결과를 로깅한다.</li>
	 *   <li>{@link EngineCommand.CancelOrder}: 호가창에서 주문을 제거하고 상태를 CANCELLED로 전이한다.</li>
	 * </ul>
	 */
	public void handle(EngineCommand command) {
		switch (command) {
			case EngineCommand.PlaceOrder c -> {
				List<Trade> trades = engine.placeLimitOrder(c.order());
				// MVP: 체결 결과를 로그로 기록. 추후 TradeRepository 저장 또는 이벤트 발행으로 대체.
				if (!trades.isEmpty()) log.info("Trades executed: {}", trades);
				orderBookCache.update(orderBook);
			}
			case EngineCommand.CancelOrder c -> {
				engine.cancelOrder(c.orderId());
				orderBookCache.update(orderBook);
			}
			case EngineCommand.Shutdown _ ->
				// EngineLoop.run()이 직접 처리하므로 여기까지 오면 로직 오류
				log.warn("Shutdown command reached EngineHandler; this should not happen.");
		}
	}
}
