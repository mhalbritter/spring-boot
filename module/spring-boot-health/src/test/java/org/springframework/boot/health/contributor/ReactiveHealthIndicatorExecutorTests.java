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
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;

import org.springframework.boot.health.contributor.ExecutorTestSupport.TimeoutEnforcingReactiveIndicator;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link ReactiveHealthIndicatorExecutor}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class ReactiveHealthIndicatorExecutorTests {

	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration SHORT_TIMEOUT = Duration.ofMillis(50);

	private static final String CALLER_CONTEXT_KEY = "caller";

	private MockEnvironment environment;

	private ReactiveHealthIndicatorExecutor executor;

	private HealthIndicatorExecutor blockingExecutor;

	@BeforeEach
	void setUp() {
		this.environment = new MockEnvironment();
		this.blockingExecutor = new HealthIndicatorExecutor(this.environment);
		this.executor = new ReactiveHealthIndicatorExecutor(this.blockingExecutor);
	}

	@AfterEach
	void tearDown() throws Exception {
		this.blockingExecutor.destroy();
	}

	@Test
	void shouldCallHealthDirectlyWhenNoTimeoutIsConfigured() {
		Health health = execute(upIndicator());
		assertThat(health.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldRemoveDetailsWhenIncludeDetailsIsFalse() {
		ReactiveHealthIndicator indicator = () -> Mono.just(Health.up().withDetail("key", "value").build());
		Health health = execute(indicator, false);
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void shouldReturnHealthWhenTimeoutIsNotExceeded() {
		setTimeout(LONG_TIMEOUT);
		Health health = execute(upIndicator());
		assertThat(health.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void shouldReturnDownWhenTimeoutIsExceeded() {
		setTimeout(SHORT_TIMEOUT);
		Health health = execute(ExecutorTestSupport.delayed(Duration.ofSeconds(2)));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldUseIndicatorEnforcementWhenTimeoutIsExceeded() {
		setTimeout(SHORT_TIMEOUT);
		Health health = execute(new TimeoutEnforcingReactiveIndicator(
				() -> Mono.just(Health.down().withDetail("reason", "timeout").build())));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void shouldUseIndicatorEnforcementWhenTimeoutIsNotExceeded() {
		setTimeout(LONG_TIMEOUT);
		TimeoutEnforcingReactiveIndicator indicator = new TimeoutEnforcingReactiveIndicator(
				() -> Mono.just(Health.up().build()));
		assertThat(execute(indicator).getStatus()).isEqualTo(Status.UP);
		assertThat(indicator.getSeenTimeout()).isEqualTo(LONG_TIMEOUT);
	}

	@Test
	void shouldPassTimeoutToFrameworkEnforcingIndicator() {
		setTimeout(LONG_TIMEOUT);
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>();
		Health health = execute(new ReactiveHealthIndicator() {

			@Override
			public Mono<Health> health() {
				return fail("Did not expect health() to be called");
			}

			@Override
			public Mono<Health> health(Duration timeout) {
				seen.set(timeout);
				return Mono.just(Health.up().build());
			}

		});
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(seen).hasValue(LONG_TIMEOUT);
	}

	@Test
	void shouldUseHealthWithDetailsOfIndicatorWhichIgnoresTimeout() {
		setTimeout(LONG_TIMEOUT);
		Health health = execute(new ReactiveHealthIndicator() {

			@Override
			public Mono<Health> health(boolean includeDetails) {
				return Mono.just(Health.up().withDetail("includeDetails", includeDetails).build());
			}

			@Override
			public Mono<Health> health() {
				return fail("Did not expect health() to be called");
			}

		});
		assertThat(health.getDetails()).containsEntry("includeDetails", true);
	}

	@Test
	void shouldCapMisdeclaredIndicatorWithFrameworkTimeout(CapturedOutput output) {
		setTimeout(SHORT_TIMEOUT);
		ReactiveHealthIndicator delayed = ExecutorTestSupport.delayed(Duration.ofSeconds(2));
		Health health = execute(new ReactiveHealthIndicator() {

			@Override
			public TimeoutEnforcement getTimeoutEnforcement() {
				return TimeoutEnforcement.INDICATOR;
			}

			@Override
			public Mono<Health> health() {
				return delayed.health();
			}

		});
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "timeout");
		assertThat(output).contains("doesn't override health(Duration)");
	}

	@Test
	void shouldJoinInFlightExecution() {
		setTimeout(LONG_TIMEOUT);
		AtomicInteger started = new AtomicInteger();
		Sinks.One<Health> result = Sinks.one();
		ReactiveHealthIndicator indicator = countingIndicator(started, result);
		List<Health> healths = new CopyOnWriteArrayList<>();
		this.executor.execute(indicator, "test", true).subscribe(healths::add);
		this.executor.execute(indicator, "test", true).subscribe(healths::add);
		result.tryEmitValue(Health.up().build());
		assertThat(started).hasValue(1);
		assertThat(healths).hasSize(2).allSatisfy((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP));
	}

	@Test
	void shouldNotPropagateContextOfCallerToSharedCheck() {
		setTimeout(LONG_TIMEOUT);
		Health health = this.executor.execute(callerReportingIndicator(), "test", true)
			.contextWrite(Context.of(CALLER_CONTEXT_KEY, "probe"))
			.block();
		assertThat(health).isNotNull();
		assertThat(health.getDetails()).containsEntry(CALLER_CONTEXT_KEY, "none");
	}

	@Test
	void shouldPropagateContextOfCallerWhenCheckIsNotShared() {
		Health health = this.executor.execute(callerReportingIndicator(), "test", true)
			.contextWrite(Context.of(CALLER_CONTEXT_KEY, "probe"))
			.block();
		assertThat(health).isNotNull();
		assertThat(health.getDetails()).containsEntry(CALLER_CONTEXT_KEY, "probe");
	}

	@Test
	void shouldStartSeparateExecutionPerIncludeDetails() {
		setTimeout(LONG_TIMEOUT);
		AtomicInteger started = new AtomicInteger();
		Sinks.One<Health> result = Sinks.one();
		ReactiveHealthIndicator indicator = countingIndicator(started, result);
		List<Health> healths = new CopyOnWriteArrayList<>();
		this.executor.execute(indicator, "test", true).subscribe(healths::add);
		this.executor.execute(indicator, "test", false).subscribe(healths::add);
		result.tryEmitValue(Health.up().withDetail("key", "value").build());
		assertThat(started).hasValue(2);
		assertThat(healths).hasSize(2);
		assertThat(healths.get(0).getDetails()).containsEntry("key", "value");
		assertThat(healths.get(1).getDetails()).isEmpty();
	}

	@Test
	void shouldReleasePermitWhenCheckTimesOut() {
		setTimeout(SHORT_TIMEOUT);
		AtomicInteger started = new AtomicInteger();
		ReactiveHealthIndicator neverEnding = () -> {
			started.incrementAndGet();
			return Mono.never();
		};
		// A reactive check ends when the deadline cancels it, so its permit is back
		// before the next probe arrives, however long the work behind it keeps running.
		for (int i = 0; i < InFlightExecutions.MAX_EXECUTIONS_PER_KEY + 1; i++) {
			Health health = execute(neverEnding);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}
		assertThat(started).hasValue(InFlightExecutions.MAX_EXECUTIONS_PER_KEY + 1);
	}

	@Test
	void shouldKeepCheckRunningWhenCallerCancels() {
		setTimeout(LONG_TIMEOUT);
		AtomicInteger started = new AtomicInteger();
		Sinks.One<Health> result = Sinks.one();
		ReactiveHealthIndicator indicator = countingIndicator(started, result);
		this.executor.execute(indicator, "test", true).subscribe().dispose();
		List<Health> healths = new CopyOnWriteArrayList<>();
		this.executor.execute(indicator, "test", true).subscribe(healths::add);
		result.tryEmitValue(Health.up().build());
		assertThat(started).hasValue(1);
		assertThat(healths).singleElement().satisfies((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP));
	}

	@Test
	void shouldRunAdaptedBlockingIndicatorOnBlockingExecutor() {
		setTimeout(LONG_TIMEOUT);
		AtomicReference<String> threadName = new AtomicReference<>();
		HealthIndicator blocking = () -> {
			threadName.set(Thread.currentThread().getName());
			return Health.up().build();
		};
		Health health = execute(adapt(blocking));
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(threadName.get()).startsWith("health-check-");
	}

	@Test
	void shouldNotJoinAdaptedBlockingIndicatorWithoutDeadline() {
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger started = new AtomicInteger();
		AtomicBoolean firstCall = new AtomicBoolean(true);
		// A check which hangs on its first call and works on every later one.
		HealthIndicator blocking = () -> {
			started.incrementAndGet();
			if (firstCall.compareAndSet(true, false)) {
				ExecutorTestSupport.awaitUninterruptibly(blocked);
			}
			return Health.up().build();
		};
		ReactiveHealthIndicator adapted = adapt(blocking);
		List<Health> healths = new CopyOnWriteArrayList<>();
		try {
			this.executor.execute(adapted, "test", true).subscribe(healths::add);
			awaitStarted(started, 1);
			// The hanging check has no deadline, so it never turns stale: joining it
			// would wedge the indicator for the lifetime of the application.
			assertThat(execute(adapted).getStatus()).isEqualTo(Status.UP);
			assertThat(healths).isEmpty();
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldApplyConcurrencyLimitToAdaptedBlockingIndicatorWithoutDeadline() {
		CountDownLatch blocked = new CountDownLatch(1);
		AtomicInteger started = new AtomicInteger();
		HealthIndicator uninterruptible = () -> {
			started.incrementAndGet();
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			return Health.up().build();
		};
		ReactiveHealthIndicator adapted = adapt(uninterruptible);
		try {
			for (int i = 0; i < InFlightExecutions.MAX_EXECUTIONS_PER_KEY; i++) {
				this.executor.execute(adapted, "test", true).subscribe();
			}
			awaitStarted(started, InFlightExecutions.MAX_EXECUTIONS_PER_KEY);
			// Unshared checks are bounded by the permits alone, so an indicator which
			// hangs reports DOWN instead of taking another thread on every probe.
			assertThat(execute(adapted).getDetails()).containsEntry("reason", "concurrency-limit");
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldApplyConcurrencyLimitToAdaptedBlockingIndicator() {
		setTimeout(SHORT_TIMEOUT);
		CountDownLatch blocked = new CountDownLatch(1);
		HealthIndicator uninterruptible = () -> {
			ExecutorTestSupport.awaitUninterruptibly(blocked);
			return Health.up().build();
		};
		ReactiveHealthIndicator adapted = adapt(uninterruptible);
		try {
			for (int i = 0; i < InFlightExecutions.MAX_EXECUTIONS_PER_KEY; i++) {
				assertThat(execute(adapted).getDetails()).containsEntry("reason", "timeout");
			}
			assertThat(execute(adapted).getDetails()).containsEntry("reason", "concurrency-limit");
		}
		finally {
			blocked.countDown();
		}
	}

	@Test
	void shouldReturnDownWhenBlockingExecutorIsDestroyed() throws Exception {
		setTimeout(LONG_TIMEOUT);
		this.blockingExecutor.destroy();
		Health health = execute(adapt(Health.up()::build));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "disposed");
	}

	@Test
	void shouldReturnDownWhenIndicatorThrowsInsteadOfSignalling() {
		ReactiveHealthIndicator indicator = () -> {
			throw new IllegalStateException("boom");
		};
		Health health = execute(indicator);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "execution-failed")
			.containsEntry("error", "java.lang.IllegalStateException: boom");
	}

	@Test
	void shouldReturnTimeoutWhenIndicatorSignalsWrappedTimeout() {
		ReactiveHealthIndicator indicator = () -> Mono.error(new CompletionException(new TimeoutException("boom")));
		Health health = execute(indicator);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "timeout")
			.containsEntry("error", "java.util.concurrent.TimeoutException: boom");
	}

	@Test
	void shouldLogFailureOfIndicatorWhenDetailsAreOmitted(CapturedOutput output) {
		ReactiveHealthIndicator indicator = () -> Mono.error(new IllegalStateException("boom"));
		assertThat(execute(indicator, false).getDetails()).isEmpty();
		assertThat(output).contains("Health indicator test failed").contains("java.lang.IllegalStateException: boom");
	}

	@Test
	void shouldOmitReasonAndExceptionWhenDetailsAreOmitted() {
		setTimeout(SHORT_TIMEOUT);
		Health health = execute(ExecutorTestSupport.delayed(Duration.ofSeconds(2)), false);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void shouldReturnDownWhenTimeoutIsInvalid() {
		setTimeout(Duration.ZERO);
		Health health = execute(upIndicator());
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "invalid-timeout");
	}

	@Test
	void shouldReturnDownWhenTimeoutOfAdaptedIndicatorIsInvalid() {
		setTimeout(Duration.ZERO);
		Health health = execute(adapt(Health.up()::build));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "invalid-timeout");
	}

	private void setTimeout(Duration timeout) {
		this.environment.setProperty("management.health.test.timeout", timeout);
	}

	private Health execute(ReactiveHealthIndicator indicator) {
		return execute(indicator, true);
	}

	private Health execute(ReactiveHealthIndicator indicator, boolean includeDetails) {
		Health health = this.executor.execute(indicator, "test", includeDetails).block(Duration.ofSeconds(5));
		assertThat(health).isNotNull();
		return health;
	}

	private static ReactiveHealthIndicator adapt(HealthIndicator indicator) {
		return (ReactiveHealthIndicator) ReactiveHealthContributor.adapt(indicator);
	}

	private static ReactiveHealthIndicator upIndicator() {
		return () -> Mono.just(Health.up().build());
	}

	/**
	 * Returns an indicator which counts its subscriptions and answers with the given
	 * sink, so that a test decides when a check ends.
	 * @param started counts the started checks
	 * @param result the result of every check
	 * @return the indicator
	 */
	private static ReactiveHealthIndicator countingIndicator(AtomicInteger started, Sinks.One<Health> result) {
		return () -> {
			started.incrementAndGet();
			return result.asMono();
		};
	}

	private static ReactiveHealthIndicator callerReportingIndicator() {
		return () -> Mono.deferContextual((context) -> {
			Object caller = context.hasKey(CALLER_CONTEXT_KEY) ? context.get(CALLER_CONTEXT_KEY) : "none";
			return Mono.just(Health.up().withDetail(CALLER_CONTEXT_KEY, caller).build());
		});
	}

	private static void awaitStarted(AtomicInteger started, int count) {
		Awaitility.await().atMost(Duration.ofSeconds(5)).untilAtomic(started, Matchers.equalTo(count));
	}

}
