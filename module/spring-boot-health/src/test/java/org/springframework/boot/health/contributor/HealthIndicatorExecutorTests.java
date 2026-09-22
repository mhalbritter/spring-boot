/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.health.contributor;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

import org.springframework.boot.health.contributor.ExecutorTestSupport.TimeoutEnforcingIndicator;
import org.springframework.boot.health.contributor.InFlightExecutions.Check;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link HealthIndicatorExecutor}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class HealthIndicatorExecutorTests {

	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);

	private MockEnvironment environment;

	private HealthIndicatorExecutor executor;

	@BeforeEach
	void setUp() {
		this.environment = new MockEnvironment();
		this.executor = new HealthIndicatorExecutor(this.environment);
	}

	@AfterEach
	void tearDown() throws Exception {
		this.executor.destroy();
	}

	@Test
	void shouldCallHealthDirectlyWhenNoTimeoutIsConfigured() {
		Health result = execute(Health.up()::build);
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldRemoveDetailsWhenIncludeDetailsIsFalse() {
		Health result = this.executor.execute(() -> Health.up().withDetail("key", "value").build(), "test", false)
			.join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
		assertThat(result.getDetails()).isEmpty();
	}

	@Test
	void shouldReturnHealthWhenTimeoutIsNotExceeded() {
		setTimeout(LONG_TIMEOUT);
		Health result = execute(Health.up()::build);
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldReturnDownAndInterruptThreadWhenTimeoutIsExceeded() {
		setTimeout(SHORT_TIMEOUT);
		AtomicBoolean interrupted = new AtomicBoolean();
		Health result = execute(ExecutorTestSupport.sleeping(Duration.ofSeconds(2), interrupted));
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAtomic(interrupted, Matchers.equalTo(true));
	}

	@Test
	void shouldReportTimeoutOfAdaptedReactiveIndicatorAsTimeout() {
		// The check runs on the pool, so the timeout is unwrapped from the failed future.
		setTimeout(LONG_TIMEOUT);
		Health result = execute(adaptTimingOutReactiveIndicator());
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldReportTimeoutOfAdaptedReactiveIndicatorOnCallingThread() {
		// Without a configured timeout the check runs on the calling thread, where the
		// timeout is unwrapped from the exception Mono#block() throws.
		Health result = execute(adaptTimingOutReactiveIndicator());
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldReportTimeoutBehindSeveralWrappers() {
		// A reactive indicator backed by a future reports the timeout wrapped twice: the
		// future wraps it in a CompletionException, Mono#block() in a ReactiveException.
		CompletableFuture<Health> timingOut = new CompletableFuture<Health>().orTimeout(50, TimeUnit.MILLISECONDS);
		ReactiveHealthIndicator reactive = () -> Mono.fromFuture(timingOut);
		Health result = execute(reactive.asHealthContributor());
		assertThat(result).isNotNull();
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldUseIndicatorEnforcementWhenTimeoutIsExceeded() {
		setTimeout(SHORT_TIMEOUT);
		Health result = execute(new TimeoutEnforcingIndicator(() -> {
			throw new TimeoutException("exceeded");
		}));
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldRunIndicatorEnforcingCheckOnCallingThread() {
		setTimeout(LONG_TIMEOUT);
		AtomicReference<Thread> checkThread = new AtomicReference<>();
		TimeoutEnforcingIndicator indicator = new TimeoutEnforcingIndicator(() -> {
			checkThread.set(Thread.currentThread());
			return Health.up().build();
		});
		CompletableFuture<@Nullable Health> result = this.executor.execute(indicator, "test", true);
		assertThat(result).isDone();
		assertThat(result.join()).isNotNull().extracting(Health::getStatus).isEqualTo(Status.UP);
		assertThat(checkThread).hasValue(Thread.currentThread());
		assertThat(indicator.getSeenTimeout()).isEqualTo(LONG_TIMEOUT);
	}

	@Test
	void shouldRunFrameworkEnforcingCheckOffCallingThread() {
		setTimeout(LONG_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicReference<Thread> checkThread = new AtomicReference<>();
		HealthIndicator indicator = () -> {
			checkThread.set(Thread.currentThread());
			ExecutorTestSupport.await(blocked);
			return Health.up().build();
		};
		try {
			CompletableFuture<@Nullable Health> result = this.executor.execute(indicator, "test", true);
			assertThat(result).isNotDone();
			blocked.countDown();
			assertThat(result.join()).isNotNull().extracting(Health::getStatus).isEqualTo(Status.UP);
			assertThat(checkThread).doesNotHaveValue(Thread.currentThread());
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldPassTimeoutToFrameworkEnforcingIndicator() {
		setTimeout(LONG_TIMEOUT);
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>();
		Health result = execute(new HealthIndicator() {

			@Override
			public Health health() {
				return fail("Did not expect health() to be called");
			}

			@Override
			public Health health(Duration timeout) {
				seen.set(timeout);
				return Health.up().build();
			}

		});
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
		assertThat(seen).hasValue(LONG_TIMEOUT);
	}

	@Test
	void shouldUseHealthWithDetailsOfIndicatorWhichIgnoresTimeout() {
		setTimeout(LONG_TIMEOUT);
		Health result = execute(new HealthIndicator() {

			@Override
			public @Nullable Health health(boolean includeDetails) {
				return Health.up().withDetail("includeDetails", includeDetails).build();
			}

			@Override
			public Health health() {
				return fail("Did not expect health() to be called");
			}

		});
		assertThat(result).isNotNull();
		assertThat(result.getDetails()).containsEntry("includeDetails", true);
	}

	@Test
	void shouldCapMisdeclaredIndicatorWithFrameworkTimeout(CapturedOutput output) {
		setTimeout(SHORT_TIMEOUT);
		HealthIndicator sleeping = ExecutorTestSupport.sleeping(Duration.ofSeconds(2));
		Health result = execute(new HealthIndicator() {

			@Override
			public TimeoutEnforcement getTimeoutEnforcement() {
				return TimeoutEnforcement.INDICATOR;
			}

			@Override
			public @Nullable Health health() {
				return sleeping.health();
			}

		});
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
		assertThat(output).contains("doesn't override health(Duration)");
	}

	@Test
	void shouldReturnDownInsteadOfThrowingAfterDestroy() throws Exception {
		setTimeout(LONG_TIMEOUT);
		this.executor.destroy();
		Health result = execute(Health.up()::build);
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "disposed");
	}

	@Test
	void shouldJoinInFlightExecution() throws Exception {
		setTimeout(LONG_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger started = new AtomicInteger();
		HealthIndicator indicator = () -> {
			started.incrementAndGet();
			ExecutorTestSupport.await(blocked);
			return Health.up().build();
		};
		CountDownLatch secondCallerArrived = new CountDownLatch(1);
		withCallers(2, (callers) -> {
			CompletableFuture<Health> first = CompletableFuture
				.supplyAsync(() -> this.executor.execute(indicator, "test", true).join(), callers);
			awaitStarted(started, 1);
			// The second caller has to reach the executor before the running check ends,
			// otherwise it starts a new execution instead of joining.
			CompletableFuture<@Nullable Health> joined = CompletableFuture.supplyAsync(() -> {
				CompletableFuture<@Nullable Health> result = this.executor.execute(indicator, "test", true);
				secondCallerArrived.countDown();
				return result.join();
			}, callers);
			ExecutorTestSupport.await(secondCallerArrived);
			blocked.countDown();
			assertThat(first.join().getStatus()).isEqualTo(Status.UP);
			assertThat(joined.join()).isNotNull().extracting(Health::getStatus).isEqualTo(Status.UP);
			assertThat(started).hasValue(1);
		}, blocked);
	}

	@Test
	void shouldNotJoinCheckWithoutDeadline() throws Exception {
		CountDownLatch blocked = new CountDownLatch(1);
		CountDownLatch firstStarted = new CountDownLatch(1);
		AtomicBoolean firstCall = new AtomicBoolean(true);
		// A check which hangs on its first call and works on every later one.
		HealthIndicator indicator = () -> {
			if (firstCall.compareAndSet(true, false)) {
				firstStarted.countDown();
				ExecutorTestSupport.awaitUninterruptibly(blocked);
			}
			return Health.up().build();
		};
		try {
			CompletableFuture<@Nullable Health> stuck = this.executor.execute(indicator, "test", true,
					ThreadingMode.POOL);
			assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
			// The hanging check has no deadline, so it never turns stale: joining it
			// would wedge the indicator for the lifetime of the application.
			Health result = this.executor.execute(indicator, "test", true, ThreadingMode.POOL).get(5, TimeUnit.SECONDS);
			assertThat(result).isNotNull();
			assertThat(result.getStatus()).isEqualTo(Status.UP);
			assertThat(stuck).isNotDone();
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldStartSeparateExecutionPerIncludeDetails() throws Exception {
		setTimeout(LONG_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger started = new AtomicInteger();
		HealthIndicator indicator = () -> {
			started.incrementAndGet();
			ExecutorTestSupport.await(blocked);
			return Health.up().build();
		};
		withCallers(2, (callers) -> {
			CompletableFuture<Health> withDetails = CompletableFuture
				.supplyAsync(() -> this.executor.execute(indicator, "test", true).join(), callers);
			awaitStarted(started, 1);
			CompletableFuture<Health> withoutDetails = CompletableFuture
				.supplyAsync(() -> this.executor.execute(indicator, "test", false).join(), callers);
			awaitStarted(started, 2);
			blocked.countDown();
			assertThat(withDetails.join().getStatus()).isEqualTo(Status.UP);
			assertThat(withoutDetails.join().getStatus()).isEqualTo(Status.UP);
		}, blocked);
	}

	@Test
	void shouldNotAbandonCallerOfCheckWithoutDeadlineOnDestroy() throws Exception {
		CountDownLatch blocked = new CountDownLatch(1);
		HealthIndicator uninterruptible = () -> {
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			return Health.up().build();
		};
		CompletableFuture<@Nullable Health> result;
		try {
			result = this.executor.execute(uninterruptible, "test", true, ThreadingMode.POOL);
			// Interrupts the check, which this one ignores.
			this.executor.destroy();
		}
		finally {
			blocked.countDown();
		}
		// The check has no deadline, so only the check itself can complete the result.
		// The pool hands every task straight to a thread, so a shutdown cannot cancel one
		// before it runs and leave its caller waiting forever.
		Health health = result.get(5, TimeUnit.SECONDS);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldNotWaitForRunningCheckOnDestroy() throws Exception {
		CountDownLatch blocked = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(1);
		HealthIndicator uninterruptible = () -> {
			started.countDown();
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			return Health.up().build();
		};
		try {
			this.executor.execute(uninterruptible, "test", true, ThreadingMode.POOL);
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
			long start = System.nanoTime();
			this.executor.destroy();
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldReturnPermitWhenCheckIsCancelledBeforeItRuns() throws Exception {
		setTimeout(SHORT_TIMEOUT);
		HealthIndicatorExecutor executor = new HealthIndicatorExecutor(this.environment) {
			@Override
			ExecutorService createExecutor() {
				return new NeverRunningExecutorService();
			}
		};
		try {
			for (int i = 0; i < InFlightExecutions.MAX_EXECUTIONS_PER_KEY + 1; i++) {
				Health result = executor.execute(Health.up()::build, "test", true).join();
				assertThat(result).isNotNull();
				assertThat(result.getDetails()).containsEntry("reason", "timeout");
			}
		}
		finally {
			executor.destroy();
		}
	}

	@Test
	void shouldKeepPermitUntilTimedOutTaskEnds() {
		setTimeout(SHORT_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger started = new AtomicInteger();
		HealthIndicator uninterruptible = () -> {
			started.incrementAndGet();
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			return Health.up().build();
		};
		try {
			for (int i = 0; i < InFlightExecutions.MAX_EXECUTIONS_PER_KEY; i++) {
				Health timedOut = execute(uninterruptible);
				assertThat(timedOut).isNotNull();
				assertThat(timedOut.getDetails()).containsEntry("reason", "timeout");
			}
			Health result = execute(uninterruptible);
			assertThat(result).isNotNull();
			assertThat(result.getDetails()).containsEntry("reason", "concurrency-limit");
			assertThat(started).hasValue(InFlightExecutions.MAX_EXECUTIONS_PER_KEY);
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldNotLeakInterruptToNextCheckOnSameThread() {
		setTimeout(SHORT_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		CountDownLatch ended = new CountDownLatch(1);
		AtomicReference<Thread> timedOutThread = new AtomicReference<>();
		// Times out, is cancelled and returns with the interrupt of that cancellation.
		HealthIndicator uninterruptible = () -> {
			timedOutThread.set(Thread.currentThread());
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			ended.countDown();
			return Health.up().build();
		};
		AtomicReference<Thread> reusedThread = new AtomicReference<>();
		AtomicBoolean interrupted = new AtomicBoolean();
		HealthIndicator recording = () -> {
			reusedThread.set(Thread.currentThread());
			interrupted.set(Thread.currentThread().isInterrupted());
			return Health.up().build();
		};
		try {
			assertThat(execute(uninterruptible)).isNotNull()
				.satisfies((health) -> assertThat(health.getDetails()).containsEntry("reason", "timeout"));
			blocked.countDown();
			ExecutorTestSupport.await(ended);
			// The pool keeps its threads, so the next check of any indicator can run on
			// the thread which was interrupted.
			Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
				execute(recording);
				return reusedThread.get() == timedOutThread.get();
			});
			assertThat(interrupted).isFalse();
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldCompleteCallersWhenReturningPermitFails() throws Exception {
		setTimeout(LONG_TIMEOUT);
		InFlightExecutions<Check> failing = new InFlightExecutions<>() {

			@Override
			void finished(Key key) {
				throw new IllegalStateException("boom");
			}

		};
		ReflectionTestUtils.setField(this.executor, "inFlight", failing);
		CompletableFuture<@Nullable Health> result = this.executor.execute(Health.up()::build, "test", true);
		// A check which cannot return its permit still has callers waiting for it.
		Health health = result.get(2, TimeUnit.SECONDS);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldReturnDownWhenTimeoutIsInvalid() {
		setTimeout(Duration.ZERO);
		Health result = execute(Health.up()::build);
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "invalid-timeout");
	}

	@Test
	void shouldReturnDownWhenIndicatorThrows() {
		Health result = execute(() -> {
			throw new IllegalStateException("boom");
		});
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "execution-failed")
			.containsEntry("error", "java.lang.IllegalStateException: boom");
	}

	@Test
	void shouldReturnDownWhenIndicatorEnforcingTimeoutThrows() {
		setTimeout(LONG_TIMEOUT);
		Health result = execute(new TimeoutEnforcingIndicator(() -> {
			throw new IllegalStateException("boom");
		}));
		assertThat(result).isNotNull();
		assertThat(result.getDetails()).containsEntry("reason", "execution-failed");
	}

	@Test
	void shouldLogFailureOfIndicatorWhenDetailsAreOmitted(CapturedOutput output) {
		HealthIndicator indicator = () -> {
			throw new IllegalStateException("boom");
		};
		Health result = this.executor.execute(indicator, "test", false).join();
		assertThat(result).isNotNull();
		assertThat(result.getDetails()).isEmpty();
		// The response says no more than DOWN, so the log has to carry the cause.
		assertThat(output).contains("Health indicator test failed").contains("java.lang.IllegalStateException: boom");
	}

	@Test
	void shouldNotLogTimeout(CapturedOutput output) {
		setTimeout(SHORT_TIMEOUT);
		execute(ExecutorTestSupport.sleeping(Duration.ofSeconds(2)));
		assertThat(output).doesNotContain("Health indicator test failed");
	}

	@Test
	void shouldOmitReasonAndExceptionWhenDetailsAreOmitted() {
		setTimeout(SHORT_TIMEOUT);
		Health result = this.executor.execute(ExecutorTestSupport.sleeping(Duration.ofSeconds(2)), "test", false)
			.join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).isEmpty();
	}

	private void setTimeout(Duration timeout) {
		this.environment.setProperty("management.health.test.timeout", timeout);
	}

	private @Nullable Health execute(HealthIndicator indicator) {
		return this.executor.execute(indicator, "test", true).join();
	}

	private static HealthIndicator adaptTimingOutReactiveIndicator() {
		ReactiveHealthIndicator reactive = () -> Mono.error(new TimeoutException("Client timed out"));
		return (HealthIndicator) reactive.asHealthContributor();
	}

	private static void awaitStarted(AtomicInteger started, int count) {
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAtomic(started, Matchers.equalTo(count));
	}

	/**
	 * Runs the given callers on a pool, unblocking the checks and shutting the pool down
	 * afterwards.
	 * @param threads how many callers run in parallel
	 * @param callers the callers
	 * @param blocked the latch the checks wait on
	 */
	private static void withCallers(int threads, CallerTask callers, CountDownLatch blocked) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			callers.run(pool);
		}
		finally {
			blocked.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	private interface CallerTask {

		void run(ExecutorService callers) throws Exception;

	}

	/**
	 * Accepts tasks, but never runs them, so a task is always cancelled before it has
	 * started.
	 */
	private static final class NeverRunningExecutorService extends AbstractExecutorService {

		@Override
		public void execute(Runnable command) {
		}

		@Override
		public void shutdown() {
		}

		@Override
		public List<Runnable> shutdownNow() {
			return Collections.emptyList();
		}

		@Override
		public boolean isShutdown() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return true;
		}

	}

}
