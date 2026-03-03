package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link EngineThread} 단위 테스트.
 *
 * <p>스레드 이름, 인터럽트 전달, graceful shutdown 동작을 검증한다.</p>
 */
@DisplayName("EngineThread")
class EngineThreadTest {

	private EngineThread engineThread;

	@BeforeEach
	void setUp() {
		engineThread = new EngineThread("BTC");
	}

	@AfterEach
	void tearDown() {
		engineThread.shutDown();
	}

	// ── start() ─────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("start()")
	class Start {

		@Test
		@DisplayName("Runnable을 'engine-thread' 이름의 스레드에서 실행한다")
		void start_executesOnNamedThread() throws InterruptedException {
			CountDownLatch latch = new CountDownLatch(1);
			AtomicReference<String> threadName = new AtomicReference<>();

			engineThread.start(() -> {
				threadName.set(Thread.currentThread().getName());
				latch.countDown();
			});

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(threadName.get()).isEqualTo("engine-thread-BTC");
		}

		@Test
		@DisplayName("Runnable이 정상 실행된다")
		void start_executesRunnable() throws InterruptedException {
			CountDownLatch latch = new CountDownLatch(1);

			engineThread.start(latch::countDown);

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		}
	}

	// ── interrupt() ─────────────────────────────────────────────────────────

	@Nested
	@DisplayName("interrupt()")
	class Interrupt {

		@Test
		@DisplayName("블로킹 중인 스레드에 인터럽트를 전달한다")
		void interrupt_interruptsBlockingThread() throws InterruptedException {
			CountDownLatch started = new CountDownLatch(1);
			CountDownLatch interrupted = new CountDownLatch(1);

			engineThread.start(() -> {
				started.countDown();
				try {
					Thread.sleep(10_000); // 블로킹 — interrupt()가 InterruptedException을 발생시킴
				} catch (InterruptedException e) {
					interrupted.countDown();
				}
			});

			assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
			engineThread.interrupt();
			assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
		}

		@Test
		@DisplayName("실행 중인 스레드가 없을 때 interrupt()를 호출해도 예외가 발생하지 않는다")
		void interrupt_withoutRunningThread_doesNotThrow() {
			// threadRef가 null인 상태에서도 안전하게 동작해야 함
			assertDoesNotThrow(() -> engineThread.interrupt());
		}
	}

	// ── shutDown() ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("shutDown()")
	class ShutDown {

		@Test
		@DisplayName("작업 완료 후 정상 종료한다")
		void shutDown_afterTaskCompletes_terminatesGracefully() throws InterruptedException {
			CountDownLatch latch = new CountDownLatch(1);

			engineThread.start(latch::countDown);

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			assertDoesNotThrow(() -> engineThread.shutDown());
		}

		@Test
		@DisplayName("실행 중인 작업이 없을 때 shutDown()을 호출해도 예외가 발생하지 않는다")
		void shutDown_withoutTask_doesNotThrow() {
			assertDoesNotThrow(() -> engineThread.shutDown());
		}
	}
}
