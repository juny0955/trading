package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.junyoung.trading.order.domain.model.entity.Order;
import dev.junyoung.trading.order.domain.model.enums.Side;
import dev.junyoung.trading.order.domain.model.value.Price;
import dev.junyoung.trading.order.domain.model.value.Quantity;
import dev.junyoung.trading.order.domain.model.value.Symbol;

/**
 * {@link EngineLoop} 단위 테스트.
 *
 * <p>Spring 컨텍스트 없이 실행한다. {@code @PostConstruct}에 해당하는 {@link EngineLoop#start()}를
 * 필요한 테스트에서 직접 호출하며, {@code @AfterEach}에서 {@link EngineLoop#stop()}으로 스레드를 정리한다.</p>
 *
 * <p>큐 용량을 3으로 설정해 오버플로우 시나리오를 빠르게 재현한다.</p>
 */
@DisplayName("EngineLoop")
class EngineLoopTest {

	private static final int QUEUE_CAPACITY = 3;

	private BlockingQueue<EngineCommand> queue;
	private EngineHandler handler;
	private EngineThread engineThread;
	private EngineLoop loop;

	@BeforeEach
	void setUp() {
		queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
		handler = mock(EngineHandler.class);
		engineThread = new EngineThread();
		loop = new EngineLoop(queue, handler, engineThread);
	}

	@AfterEach
	void tearDown() {
		queue.clear();
		loop.stop();
	}

	private static final Symbol SYMBOL = new Symbol("BTC");

	private EngineCommand.PlaceOrder placeOrderCommand() {
		Order order = Order.createLimit(Side.BUY, SYMBOL, new Price(10_000), new Quantity(5));
		return new EngineCommand.PlaceOrder(order);
	}

	// ── submit() ────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("submit()")
	class Submit {

		@Test
		@DisplayName("커맨드를 큐에 추가한다")
		void submit_addsCommandToQueue() {
			EngineCommand command = placeOrderCommand();

			loop.submit(command);

			assertThat(queue).containsExactly(command);
		}

		@Test
		@DisplayName("큐가 가득 차면 IllegalStateException이 발생한다")
		void submit_throwsWhenQueueFull() {
			// QUEUE_CAPACITY(3)만큼 채움
			loop.submit(placeOrderCommand());
			loop.submit(placeOrderCommand());
			loop.submit(placeOrderCommand());

			assertThrows(IllegalStateException.class, () -> loop.submit(placeOrderCommand()));
		}

		@Test
		@DisplayName("큐가 비어있으면 정상 추가된다")
		void submit_emptyQueue_succeeds() {
			assertDoesNotThrow(() -> loop.submit(placeOrderCommand()));
		}
	}

	// ── run() ───────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("큐에서 커맨드를 꺼내 EngineHandler로 전달한다")
		void run_processesCommandViaHandler() throws InterruptedException {
			CountDownLatch latch = new CountDownLatch(1);
			EngineCommand command = placeOrderCommand();
			doAnswer(_ -> { latch.countDown(); return null; }).when(handler).handle(command);

			loop.start();
			loop.submit(command);

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			verify(handler).handle(command);
		}

		@Test
		@DisplayName("커맨드를 순서대로 처리한다")
		void run_processesCommandsInOrder() throws InterruptedException {
			int commandCount = 3;
			CountDownLatch latch = new CountDownLatch(commandCount);
			doAnswer(_ -> { latch.countDown(); return null; }).when(handler).handle(any());

			loop.start();
			for (int i = 0; i < commandCount; i++) {
				loop.submit(placeOrderCommand());
			}

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		}

		@Test
		@DisplayName("핸들러에서 예외가 발생해도 루프가 중단되지 않는다")
		void run_handlerException_loopContinues() throws InterruptedException {
			CountDownLatch firstLatch = new CountDownLatch(1);
			CountDownLatch secondLatch = new CountDownLatch(1);

			doAnswer(_ -> { firstLatch.countDown(); throw new RuntimeException("test error"); })
				.doAnswer(_ -> { secondLatch.countDown(); return null; })
				.when(handler).handle(any());

			loop.start();
			loop.submit(placeOrderCommand());
			loop.submit(placeOrderCommand());

			// 첫 번째 커맨드 예외 발생 후 두 번째 커맨드가 정상 처리됨
			assertThat(firstLatch.await(2, TimeUnit.SECONDS)).isTrue();
			assertThat(secondLatch.await(2, TimeUnit.SECONDS)).isTrue();
		}
	}

	// ── stop() ──────────────────────────────────────────────────────────────

	@Nested
	@DisplayName("stop()")
	class Stop {

		@Test
		@DisplayName("start() 없이 stop()을 호출해도 예외가 발생하지 않는다")
		void stop_withoutStart_doesNotThrow() {
			assertDoesNotThrow(() -> loop.stop());
		}

		@Test
		@DisplayName("start() 후 stop()을 호출하면 정상 종료한다")
		void stop_afterStart_terminatesGracefully() throws InterruptedException {
			CountDownLatch started = new CountDownLatch(1);
			doAnswer(_ -> { started.countDown(); return null; }).when(handler).handle(any());

			loop.start();
			loop.submit(placeOrderCommand());

			assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
			assertDoesNotThrow(() -> loop.stop());
		}

		@Test
		@DisplayName("stop() 전 제출된 커맨드가 모두 처리된 후 종료된다")
		void stop_drainsQueueBeforeTerminating() {
			int commandCount = 2; // QUEUE_CAPACITY(3) - Shutdown(1)
			CountDownLatch allProcessed = new CountDownLatch(commandCount);
			doAnswer(_ -> { allProcessed.countDown(); return null; }).when(handler).handle(any());

			// 루프 시작 전에 큐에 커맨드를 쌓아둔다
			for (int i = 0; i < commandCount; i++) {
				loop.submit(placeOrderCommand());
			}

			loop.start();
			loop.stop(); // Poison Pill 삽입 → 큐 소진 후 종료 대기

			// stop()이 반환되면 모든 커맨드가 처리되어 있어야 한다
			assertThat(allProcessed.getCount()).isZero();
		}

		@Test
		@DisplayName("stop() 후 submit() 호출 시 예외가 발생한다")
		void submit_afterStop_throwsIllegalStateException() {
			loop.stop();

			assertThrows(IllegalStateException.class, () -> loop.submit(placeOrderCommand()));
		}

		@Test
		@DisplayName("Shutdown 커맨드는 EngineHandler.handle()로 전달되지 않는다")
		void stop_shutdownCommandIsNotPassedToHandler() {
			loop.submit(placeOrderCommand());
			loop.start();
			loop.stop(); // shutDown()이 스레드 종료를 보장

			// PlaceOrder 1번만 처리됨; Shutdown은 handle()에 전달되지 않음
			verify(handler, times(1)).handle(any());
			verify(handler, never()).handle(any(EngineCommand.Shutdown.class));
		}

		@Test
		@DisplayName("큐가 꽉 찼을 때 stop()은 엔진이 드레이닝할 때까지 대기한 후 종료된다")
		void stop_whenQueueFull_blocksUntilDrained() throws InterruptedException {
			CountDownLatch firstHandled = new CountDownLatch(1);
			CountDownLatch releaseHandler = new CountDownLatch(1);

			// 첫 번째 커맨드 처리 중 블로킹 → 큐가 꽉 찬 상태 유지 가능
			doAnswer(_ -> {
				firstHandled.countDown();
				try { releaseHandler.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				return null;
			}).doAnswer(_ -> null).when(handler).handle(any());

			loop.start();
			loop.submit(placeOrderCommand()); // 엔진이 처리 시작 → 핸들러 블로킹
			assertThat(firstHandled.await(2, TimeUnit.SECONDS)).isTrue();

			// 핸들러가 블로킹된 동안 큐를 가득 채움 (QUEUE_CAPACITY = 3)
			loop.submit(placeOrderCommand());
			loop.submit(placeOrderCommand());
			loop.submit(placeOrderCommand());

			// stop()을 별도 스레드에서 호출: 큐가 꽉 찼으므로 put()이 블로킹됨
			Thread stopThread = new Thread(() -> loop.stop(), "test-stop-thread");
			stopThread.start();
			Thread.sleep(100); // stop()이 put() 블로킹 상태에 진입할 시간

			// 핸들러 해제 → 엔진이 드레이닝 → put() 성공 → stop() 완료
			releaseHandler.countDown();

			stopThread.join(5_000);
			assertThat(stopThread.isAlive()).isFalse();
		}

		@Test
		@DisplayName("stop()을 여러 번 호출해도 예외가 발생하지 않는다")
		void stop_calledMultipleTimes_doesNotThrow() {
			loop.start();

			assertDoesNotThrow(() -> {
				loop.stop();
				loop.stop();
				loop.stop();
			});
		}
	}
}
