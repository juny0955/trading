package dev.junyoung.trading.engine.application.engine.loop;

import dev.junyoung.trading.engine.application.engine.EngineManager;
import dev.junyoung.trading.engine.application.engine.handler.EngineHandler;
import dev.junyoung.trading.engine.application.engine.runtime.EngineRuntimeOwner;
import dev.junyoung.trading.engine.application.engine.runtime.EngineSymbolState;
import dev.junyoung.trading.engine.application.exception.EngineQueueFullException;
import dev.junyoung.trading.engine.application.metrics.EngineMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

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
@RequiredArgsConstructor
@Slf4j
public class EngineLoop implements Runnable {

	// -------------------------------------------------------------------------
	// 생성자
	// -------------------------------------------------------------------------

	private final BlockingQueue<EngineCommand> engineQueue;
	private final EngineHandler engineHandler;
	private final EngineThread engineThread;
	private final EngineRuntimeOwner runtimeOwner;
	private final EngineMetrics engineMetrics;

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

	// -------------------------------------------------------------------------
	// 진입점
	// -------------------------------------------------------------------------

	/** engine-thread를 시작한다. {@link dev.junyoung.trading.engine.application.engine.runtime.EngineRuntime}의 생성자에서 호출된다. */
	public void start() {
		engineThread.start(this);
	}

	/** 루프를 중단하고 스레드를 정리한다. {@link EngineManager}의 {@code @PreDestroy}에서 호출된다. */
	public void stop() {
		submitLock.lock();
		try {
			running = false;
			engineQueue.put(new EngineCommand.Shutdown());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			engineThread.interrupt();
		} finally {
			submitLock.unlock();
		}

		engineThread.shutDown();
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
			command = stampEnqueuedAt(command);
			if (!engineQueue.offer(command)) throw new EngineQueueFullException();
		} finally {
			submitLock.unlock();
		}
	}

	// -------------------------------------------------------------------------
	// 루프 본체
	// -------------------------------------------------------------------------

	/**
	 * engine-thread에서 실행되는 이벤트 루프 본체.
	 * {@link InterruptedException}은 정상 종료 신호로 처리하고,
	 * 그 외 예외는 개별 커맨드 실패로 간주해 로그만 남기고 루프를 유지한다.
	 */
	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				EngineCommand command = engineQueue.take();
				if (command instanceof EngineCommand.Shutdown)
					break;

				Instant dequeuedAt = Instant.now();
				if (command instanceof EngineCommand.PlaceOrder p && p.enqueuedAt() != null)
					engineMetrics.recordQueueWaitLatency(Duration.between(p.enqueuedAt(), dequeuedAt));
				else if (command instanceof EngineCommand.CancelOrder c && c.enqueuedAt() != null)
					engineMetrics.recordQueueWaitLatency(Duration.between(c.enqueuedAt(), dequeuedAt));

				engineHandler.handle(command);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.error("Engine Command Failed", e);
				if (runtimeOwner.state() == EngineSymbolState.REBUILDING)
					runtimeOwner.attemptRebuild();
			}
		}
	}

	private EngineCommand stampEnqueuedAt(EngineCommand command) {
		Instant now = Instant.now();
		return switch (command) {
			case EngineCommand.PlaceOrder p -> new EngineCommand.PlaceOrder(p.order(), p.serviceEnteredAt(), now);
			case EngineCommand.CancelOrder c -> new EngineCommand.CancelOrder(c.acceptedSeq(), c.orderId(), c.requesterAccountId(), c.serviceEnteredAt(), now);
			default -> command;
		};
	}
}
