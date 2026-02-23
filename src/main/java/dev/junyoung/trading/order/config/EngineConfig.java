package dev.junyoung.trading.order.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.junyoung.trading.order.application.engine.EngineCommand;
import dev.junyoung.trading.order.domain.model.OrderBook;

/**
 * 매칭 엔진 관련 빈(Bean) 설정.
 *
 * <p>{@link OrderBook}과 엔진 커맨드 큐를 스프링 컨텍스트에 등록한다.
 * 두 빈 모두 단일 인스턴스로 관리되며, 상태 변경은 항상 engine-thread에서만 발생한다.</p>
 */
@Configuration
public class EngineConfig {

	/**
	 * 인메모리 단일 종목 호가창.
	 * {@code MatchingEngine}이 주입받아 독점적으로 읽고 쓴다.
	 */
	@Bean
	public OrderBook orderBook() {
		return new OrderBook();
	}

	/**
	 * 매칭 엔진 커맨드 큐.
	 *
	 * <ul>
	 *   <li>용량 10,000건의 유계(bounded) 큐. 초과 시 {@code EngineLoop.submit()}에서 예외 발생.</li>
	 *   <li>HTTP 스레드(생산자)와 engine-thread(소비자) 간 유일한 공유 자원이다.</li>
	 * </ul>
	 */
	@Bean
	public BlockingQueue<EngineCommand> engineQueue() {
		return new ArrayBlockingQueue<>(10_000);
	}
}
