package dev.junyoung.trading.order.application.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
		loop.stop();
	}

	private EngineCommand.PlaceOrder placeOrderCommand() {
		Order order = new Order(Side.BUY, new Price(10_000), new Quantity(5));
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
	}
}
