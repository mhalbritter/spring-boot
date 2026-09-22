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
		assertThat(joinOrStart(KEY)).isNotNull();
		assertThat(this.started).hasValue(1);
	}

	@Test
	void shouldJoinRunningCheck() {
		Execution<TestCheck> first = joinOrStart(KEY);
		Execution<TestCheck> second = joinOrStart(KEY);
		assertThat(second).isSameAs(first);
		assertThat(this.started).hasValue(1);
	}

	@Test
	void shouldNotJoinCheckStartedWithoutDeadline() {
		TestCheck check = start(KEY);
		assertThat(start(KEY)).isNotSameAs(check);
		assertThat(joinOrStart(KEY).check()).isNotSameAs(check);
		assertThat(this.started).hasValue(3);
	}

	@Test
	void shouldFailWhenIndicatorHasTooManyChecksStartedWithoutDeadline() {
		for (int i = 0; i < MAX_EXECUTIONS_PER_KEY; i++) {
			assertThat(start(KEY)).isNotNull();
		}
		assertThatExceptionOfType(TooManyChecksInFlightException.class).isThrownBy(() -> start(KEY));
		assertThat(this.started).hasValue(MAX_EXECUTIONS_PER_KEY);
	}

	@Test
	void shouldKeepNewerExecutionWhenCheckFailsToStart() {
		AtomicReference<Execution<TestCheck>> replacement = new AtomicReference<>();
		assertThatIllegalStateException()
			.isThrownBy(() -> this.executions.joinOrStart(KEY, TIMEOUT, () -> new TestCheck(() -> {
				// The execution turns stale and is replaced before its check fails.
				this.clock.addAndGet(TIMEOUT.toNanos());
				replacement.set(joinOrStart(KEY));
				throw new IllegalStateException("Unable to start");
			})));
		assertThat(joinOrStart(KEY)).isSameAs(replacement.get());
	}

	@Test
	void shouldReturnPermitWhenStarterOfCheckWithoutDeadlineFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.executions.start(KEY, () -> {
			throw new IllegalStateException("Unable to start");
		}));
		assertThat(start(KEY)).isNotNull();
	}

	@Test
	void shouldReturnPermitWhenCheckWithoutDeadlineFailsToStart() {
		assertThatIllegalStateException().isThrownBy(() -> this.executions.start(KEY, () -> new TestCheck(() -> {
			throw new IllegalStateException("Unable to start");
		})));
		assertThat(start(KEY)).isNotNull();
	}

	@Test
	void shouldNotJoinCheckOfOtherIncludeDetails() {
		Execution<TestCheck> withDetails = joinOrStart(KEY);
		Execution<TestCheck> withoutDetails = joinOrStart(new Key(KEY.indicatorName(), false));
		assertThat(withoutDetails).isNotSameAs(withDetails);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldStartNewCheckWhenRunningOneIsStale() {
		Execution<TestCheck> first = joinOrStart(KEY);
		this.clock.addAndGet(TIMEOUT.toNanos());
		Execution<TestCheck> second = joinOrStart(KEY);
		assertThat(second).isNotSameAs(first);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldStartNewCheckWhenRunningOneHasEnded() {
		Execution<TestCheck> first = joinOrStart(KEY);
		first.check().end();
		Execution<TestCheck> second = joinOrStart(KEY);
		assertThat(second).isNotSameAs(first);
		assertThat(this.started).hasValue(2);
	}

	@Test
	void shouldFailWhenIndicatorHasTooManyChecksInFlight() {
		saturate(KEY);
		assertThatExceptionOfType(TooManyChecksInFlightException.class).isThrownBy(() -> joinOrStart(KEY))
			.withMessage("Health indicator test already has 4 checks in flight");
		assertThat(this.started).hasValue(MAX_EXECUTIONS_PER_KEY);
	}

	@Test
	void shouldApplyLimitPerIndicatorName() {
		saturate(KEY);
		assertThat(joinOrStart(new Key("other", true))).isNotNull();
	}

	@Test
	void shouldApplyLimitPerIncludeDetails() {
		saturate(KEY);
		assertThat(joinOrStart(new Key(KEY.indicatorName(), false))).isNotNull();
	}

	@Test
	void shouldReturnPermitWhenCheckHasFinished() {
		saturate(KEY);
		this.executions.finished(KEY);
		assertThat(joinOrStart(KEY)).isNotNull();
	}

	@Test
	void shouldStartCheckOutsideRegistryUpdate() {
		AtomicReference<Execution<TestCheck>> joinedWhileStarting = new AtomicReference<>();
		Execution<TestCheck> execution = this.executions.joinOrStart(KEY, TIMEOUT,
				() -> new TestCheck(() -> joinedWhileStarting.set(joinOrStart(KEY))));
		assertThat(joinedWhileStarting.get()).isSameAs(execution);
		assertThat(this.started).hasValue(0);
	}

	@Test
	void shouldReturnPermitWhenStarterFails() {
		assertThatIllegalStateException().isThrownBy(() -> this.executions.joinOrStart(KEY, TIMEOUT, () -> {
			throw new IllegalStateException("Unable to start");
		}));
		assertThat(joinOrStart(KEY)).isNotNull();
	}

	@Test
	void shouldNotRetainStateOfIndicatorsWithoutChecksInFlight() {
		for (int i = 0; i < 100; i++) {
			Key key = new Key("indicator-" + i, true);
			joinOrStart(key);
			this.executions.finished(key);
		}
		assertThat(this.executions).extracting("inFlightCounts", InstanceOfAssertFactories.MAP).isEmpty();
	}

	private Execution<TestCheck> joinOrStart(Key key) {
		return this.executions.joinOrStart(key, TIMEOUT, this::newCheck);
	}

	private TestCheck start(Key key) {
		return this.executions.start(key, this::newCheck);
	}

	private TestCheck newCheck() {
		this.started.incrementAndGet();
		return new TestCheck();
	}

	/**
	 * Uses up all permits of an indicator with checks which are stale, but have not
	 * ended.
	 * @param key the key to saturate
	 */
	private void saturate(Key key) {
		for (int i = 0; i < MAX_EXECUTIONS_PER_KEY; i++) {
			joinOrStart(key);
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
