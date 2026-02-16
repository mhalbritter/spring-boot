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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.fail;

/**
 * Indicators and latch helpers shared by {@link HealthIndicatorExecutorTests} and
 * {@link ReactiveHealthIndicatorExecutorTests}.
 *
 * @author Moritz Halbritter
 */
final class ExecutorTestSupport {

	private static final Duration LATCH_TIMEOUT = Duration.ofSeconds(10);

	private ExecutorTestSupport() {
	}

	/**
	 * Returns an indicator which sleeps, and therefore ends when it is interrupted.
	 * @param sleep how long to sleep
	 * @return the indicator
	 */
	static HealthIndicator sleeping(Duration sleep) {
		return sleeping(sleep, new AtomicBoolean());
	}

	/**
	 * Returns an indicator which sleeps, and sets the given flag when it is interrupted.
	 * @param sleep how long to sleep
	 * @param interrupted set when the sleep is interrupted
	 * @return the indicator
	 */
	static HealthIndicator sleeping(Duration sleep, AtomicBoolean interrupted) {
		return () -> {
			try {
				Thread.sleep(sleep.toMillis());
			}
			catch (InterruptedException ex) {
				interrupted.set(true);
				Thread.currentThread().interrupt();
				throw new RuntimeException(ex);
			}
			return Health.up().build();
		};
	}

	/**
	 * Returns a reactive indicator which answers after a delay, and therefore ends when
	 * it is cancelled.
	 * @param delay how long to delay the answer
	 * @return the indicator
	 */
	static ReactiveHealthIndicator delayed(Duration delay) {
		return () -> Mono.just(Health.up().build()).delayElement(delay);
	}

	static void await(CountDownLatch latch) {
		try {
			if (!latch.await(LATCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IllegalStateException("Latch was not counted down in time");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(ex);
		}
	}

	static void awaitUninterruptibly(CountDownLatch latch) {
		boolean interrupted = false;
		try {
			while (latch.getCount() > 0) {
				try {
					if (!latch.await(LATCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
						throw new IllegalStateException("Latch was not counted down in time");
					}
				}
				catch (InterruptedException ex) {
					interrupted = true;
				}
			}
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * An indicator which caps the check itself and records the timeout it was given.
	 */
	static final class TimeoutEnforcingIndicator implements HealthIndicator {

		private final AtomicReference<@Nullable Duration> seenTimeout = new AtomicReference<>();

		private final Callable<@Nullable Health> check;

		TimeoutEnforcingIndicator(Callable<@Nullable Health> check) {
			this.check = check;
		}

		@Override
		public TimeoutEnforcement getTimeoutEnforcement() {
			return TimeoutEnforcement.INDICATOR;
		}

		@Override
		public @Nullable Health health(Duration timeout) throws TimeoutException {
			this.seenTimeout.set(timeout);
			try {
				return this.check.call();
			}
			catch (TimeoutException | RuntimeException ex) {
				throw ex;
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		@Override
		public @Nullable Health health() {
			return fail("Did not expect health() to be called");
		}

		@Nullable Duration getSeenTimeout() {
			return this.seenTimeout.get();
		}

	}

	/**
	 * A reactive indicator which caps the check itself and records the timeout it was
	 * given.
	 */
	static final class TimeoutEnforcingReactiveIndicator implements ReactiveHealthIndicator {

		private final AtomicReference<@Nullable Duration> seenTimeout = new AtomicReference<>();

		private final Supplier<Mono<Health>> check;

		TimeoutEnforcingReactiveIndicator(Supplier<Mono<Health>> check) {
			this.check = check;
		}

		@Override
		public TimeoutEnforcement getTimeoutEnforcement() {
			return TimeoutEnforcement.INDICATOR;
		}

		@Override
		public Mono<Health> health(Duration timeout) {
			this.seenTimeout.set(timeout);
			return this.check.get();
		}

		@Override
		public Mono<Health> health() {
			return fail("Did not expect health() to be called");
		}

		@Nullable Duration getSeenTimeout() {
			return this.seenTimeout.get();
		}

	}

}
