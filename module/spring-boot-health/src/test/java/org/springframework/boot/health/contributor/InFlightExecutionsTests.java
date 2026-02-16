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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.InFlightExecutions.Check;
import org.springframework.boot.health.contributor.InFlightExecutions.Execution;
import org.springframework.boot.health.contributor.InFlightExecutions.Key;
import org.springframework.boot.health.contributor.InFlightExecutions.TooManyChecksInFlightException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link InFlightExecutions}.
 *
 * @author Moritz Halbritter
 */
class InFlightExecutionsTests {

	private static final int MAX_EXECUTIONS_PER_KEY = 4;

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final Key KEY = new Key("test", true);

	private AtomicLong clock;

	private AtomicInteger started;

	private InFlightExecutions<TestCheck> executions;

	@BeforeEach
	void setUp() {
		this.clock = new AtomicLong();
		this.started = new AtomicInteger();
		this.executions = new InFlightExecutions<>(MAX_EXECUTIONS_PER_KEY, this.clock::get);
	}

	@Test
	void shouldStartCheckWhenNoneIsRunning() {
		assertThat(join(KEY)).isNotNull();
		assertThat(this.started).hasValue(1);
	}

	@Test
	void shouldJoinRunningCheck() {
		Execution<TestCheck> first = join(KEY);
		Execution<TestCheck> second = join(KEY);
		assertThat(second).isSameAs(first);
		assertThat(this.started).hasValue(1);
	}

	@Test
	void shouldStayJoinableWithoutDeadline() {
		Execution<TestCheck> execution = join(KEY, null);
		this.clock.addAndGet(TIMEOUT.multipliedBy(100).toNanos());
		assertThat(join(KEY, null)).isSameAs(execution);
		assertThat(this.started).hasValue(1);
	}

	@Test
	void shouldStartNewExecutionWhenCheckWithoutDeadlineEnds() {
		Execution<TestCheck> execution = join(KEY, null);
		execution.check().end();
		assertThat(join(KEY, null)).isNotSameAs(execution);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldNotJoinCheckOfOtherIncludeDetails() {
		Execution<TestCheck> withDetails = join(KEY);
		Execution<TestCheck> withoutDetails = join(new Key(KEY.indicatorName(), false));
		assertThat(withoutDetails).isNotSameAs(withDetails);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldStartNewCheckWhenRunningOneIsStale() {
		Execution<TestCheck> first = join(KEY);
		this.clock.addAndGet(TIMEOUT.toNanos());
		Execution<TestCheck> second = join(KEY);
		assertThat(second).isNotSameAs(first);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldStartNewCheckWhenRunningOneHasEnded() {
		Execution<TestCheck> first = join(KEY);
		first.check().end();
		Execution<TestCheck> second = join(KEY);
		assertThat(second).isNotSameAs(first);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldFailWhenIndicatorHasTooManyChecksInFlight() {
		saturate(KEY);
		assertThatExceptionOfType(TooManyChecksInFlightException.class).isThrownBy(() -> join(KEY))
			.withMessage("Health indicator test already has 4 checks in flight");
		assertThat(this.started).hasValue(MAX_EXECUTIONS_PER_KEY);
	}

	@Test
	void shouldApplyLimitPerIndicatorName() {
		saturate(KEY);
		assertThat(join(new Key("other", true))).isNotNull();
	}

	@Test
	void shouldApplyLimitPerIncludeDetails() {
		saturate(KEY);
		assertThat(join(new Key(KEY.indicatorName(), false))).isNotNull();
	}

	@Test
	void shouldReturnPermitWhenCheckHasFinished() {
		saturate(KEY);
		this.executions.finished(KEY);
		assertThat(join(KEY)).isNotNull();
	}

	@Test
	void shouldStartCheckOutsideRegistryUpdate() {
		AtomicReference<Execution<TestCheck>> joinedWhileStarting = new AtomicReference<>();
		Execution<TestCheck> execution = this.executions.join(KEY, TIMEOUT,
				() -> new TestCheck(() -> joinedWhileStarting.set(join(KEY))));
		assertThat(joinedWhileStarting.get()).isSameAs(execution);
		assertThat(this.started).hasValue(0);
	}

	@Test
	void shouldReturnPermitWhenStarterFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.executions.join(KEY, TIMEOUT, () -> {
			throw new IllegalStateException("Unable to start");
		}));
		assertThat(join(KEY)).isNotNull();
	}

	@Test
	void shouldNotRetainStateOfIndicatorsWithoutChecksInFlight() {
		for (int i = 0; i < 100; i++) {
			Key key = new Key("indicator-" + i, true);
			join(key);
			this.executions.finished(key);
		}
		assertThat(this.executions).extracting("inFlightCounts", InstanceOfAssertFactories.MAP).isEmpty();
	}

	private Execution<TestCheck> join(Key key) {
		return join(key, TIMEOUT);
	}

	private Execution<TestCheck> join(Key key, @Nullable Duration timeout) {
		return this.executions.join(key, timeout, () -> {
			this.started.incrementAndGet();
			return new TestCheck();
		});
	}

	/**
	 * Uses up all permits of an indicator with checks which are stale, but have not
	 * ended.
	 * @param key the key to saturate
	 */
	private void saturate(Key key) {
		for (int i = 0; i < MAX_EXECUTIONS_PER_KEY; i++) {
			join(key);
			this.clock.addAndGet(TIMEOUT.toNanos());
		}
	}

	private static final class TestCheck implements Check {

		private final @Nullable Runnable onStart;

		private boolean ended;

		private TestCheck() {
			this(null);
		}

		private TestCheck(@Nullable Runnable onStart) {
			this.onStart = onStart;
		}

		@Override
		public void start() {
			if (this.onStart != null) {
				this.onStart.run();
			}
		}

		private void end() {
			this.ended = true;
		}

		@Override
		public boolean hasEnded() {
			return this.ended;
		}

	}

}
