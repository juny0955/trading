package dev.junyoung.trading.order.application.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매칭 엔진의 단일 스레드 이벤트 루프.
 *
 * <p>HTTP 스레드는 {@link #submit}으로 커맨드를 큐에 넣고 즉시 반환한다(fire-and-forget).
 * engine-thread는 큐에서 커맨드를 순차적으로 꺼내 {@link EngineHandler}로 전달한다.
 * OrderBook과 Order의 상태 변경은 반드시 이 단일 스레드에서만 발생하므로 Race Condition이 없다.</p>
 *
 * <pre>
 * HTTP 스레드: submit(command) → BlockingQueue.offer()
 * engine-thread: BlockingQueue.take() → EngineHandler.handle()
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineLoop implements Runnable {

	private final BlockingQueue<EngineCommand> engineQueue;
	private final EngineHandler engineHandler;
	private final EngineThread engineThread;

	/**
	 * 루프 종료 플래그.
	 * {@link #submitLock}을 보유한 상태에서만 읽고 쓰므로 {@code volatile} 불필요.
	 */
	private boolean running = true;

	/**
	 * {@link #submit}의 check-then-act(running 확인 → 큐 삽입)와
	 * {@link #stop}의 running 변경을 원자적으로 묶어 TOCTOU를 방지한다.
	 */
	private final ReentrantLock submitLock = new ReentrantLock();

	/** Spring 빈 초기화 후 engine-thread를 시작한다. */
	@PostConstruct
	public void start() {
		engineThread.start(this);
	}

	/** Spring 컨텍스트 종료 시 루프를 중단하고 스레드를 정리한다. */
	@PreDestroy
	public void stop() {
		submitLock.lock();
		try {
			running = false;
			// 락 보유 중 삽입하므로 Shutdown이 항상 마지막 커맨드임을 보장한다.
			// 큐가 가득 찼으면 engine-thread가 드레이닝할 때까지 대기(put)한다.
			engineQueue.put(new EngineCommand.Shutdown());
		} catch (InterruptedException e) {
			// shutdown 스레드 자체가 인터럽트된 경우 → interrupt()로 take() 블로킹 해제
			Thread.currentThread().interrupt();
			engineThread.interrupt();
		} finally {
			submitLock.unlock();
		}

		engineThread.shutDown();  // ExecutorService 종료 대기
	}

	/**
	 * 커맨드를 큐에 제출한다. engine-thread가 비동기로 처리한다.
	 *
	 * <p>{@code running} 확인과 큐 삽입을 {@link #submitLock}으로 묶어
	 * Shutdown 이후 커맨드가 큐에 유입되는 TOCTOU를 방지한다.</p>
	 *
	 * @throws IllegalStateException 엔진이 종료 중이거나 큐가 가득 찬 경우 (용량: {@code ArrayBlockingQueue(10_000)})
	 */
	public void submit(EngineCommand command) {
		submitLock.lock();
		try {
			if (!running) throw new IllegalStateException("Engine is shutting down");
			if (!engineQueue.offer(command)) throw new IllegalStateException("Engine Queue is full");
		} finally {
			submitLock.unlock();
		}
	}

	/**
	 * engine-thread에서 실행되는 이벤트 루프 본체.
	 * {@link InterruptedException}은 정상 종료 신호로 처리하고,
	 * 그 외 예외는 개별 커맨드 실패로 간주해 로그만 남기고 루프를 유지한다.
	 */
	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				EngineCommand command = engineQueue.take(); // 커맨드가 올 때까지 블로킹
				if (command instanceof EngineCommand.Shutdown) {
					break;
				}
				engineHandler.handle(command);
			} catch (InterruptedException e) {
				// stop()에서 interrupt()를 호출했을 때 발생 → 루프 정상 종료
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				// 특정 커맨드 처리 실패가 전체 엔진을 멈추지 않도록 예외를 격리
				log.error("Engine Command Failed", e);
			}
		}
	}
}
