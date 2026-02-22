package dev.junyoung.trading.order.application.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * 매칭 엔진 전용 단일 스레드를 관리하는 컴포넌트.
 *
 * <p>{@link Executors#newSingleThreadExecutor}로 생성된 스레드를 {@code "engine-thread"}로
 * 명명해 스레드 덤프에서 식별하기 쉽게 한다.</p>
 *
 * <p>{@link EngineLoop}가 이 클래스를 통해 스레드를 시작·중단한다.
 * {@link #interrupt()}는 {@code BlockingQueue.take()} 블로킹을 해제하기 위해 사용된다.</p>
 */
@Component
public class EngineThread {

	private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r);
		thread.setName("engine-thread");
		return thread;
	});

	/**
	 * 실행 중인 engine-thread의 참조. {@link #interrupt()} 호출 시 사용한다.
	 * {@code AtomicReference}로 선언해 다른 스레드에서 안전하게 읽고 쓸 수 있다.
	 */
	private final AtomicReference<Thread> threadRef = new AtomicReference<>();

	/**
	 * engine-thread에서 {@code runnable}을 실행한다.
	 * 실행 시작 시 {@code threadRef}에 현재 스레드를 등록하고, 종료 시 초기화한다.
	 */
	public void start(Runnable runnable) {
		executorService.submit(() -> {
			threadRef.set(Thread.currentThread());
			try {
				runnable.run();
			} finally {
				threadRef.set(null);
			}
		});
	}

	/**
	 * engine-thread에 인터럽트를 전달한다.
	 * 주로 {@code BlockingQueue.take()}의 블로킹을 해제하기 위해 호출된다.
	 */
	public void interrupt() {
		Thread thread = threadRef.get();
		if (thread != null) thread.interrupt();
	}

	/**
	 * ExecutorService를 종료한다. 2초 내 종료되지 않으면 강제 중단한다.
	 */
	public void shutDown() {
		executorService.shutdown();
		try {
			if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			executorService.shutdownNow();
		}
	}
}
