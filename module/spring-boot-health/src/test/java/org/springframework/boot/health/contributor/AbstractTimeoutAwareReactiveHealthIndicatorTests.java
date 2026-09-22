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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractTimeoutAwareReactiveHealthIndicator}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class AbstractTimeoutAwareReactiveHealthIndicatorTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	@Test
	void shouldEnforceTimeoutItself() {
		assertThat(new TestReactiveHealthIndicator((builder, timeout) -> Mono.just(builder.up().build()))
			.getTimeoutEnforcement()).isEqualTo(TimeoutEnforcement.INDICATOR);
	}

	@Test
	void shouldPassTimeoutToHealthCheck() {
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>();
		TestReactiveHealthIndicator indicator = new TestReactiveHealthIndicator((builder, timeout) -> {
			seen.set(timeout);
			return Mono.just(builder.up().build());
		});
		StepVerifier.create(indicator.health(TIMEOUT))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.verifyComplete();
		assertThat(seen).hasValue(TIMEOUT);
	}

	@Test
	void shouldPassNullTimeoutToHealthCheckWhenNoTimeoutIsConfigured() {
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>(TIMEOUT);
		TestReactiveHealthIndicator indicator = new TestReactiveHealthIndicator((builder, timeout) -> {
			seen.set(timeout);
			return Mono.just(builder.up().build());
		});
		StepVerifier.create(indicator.health())
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.verifyComplete();
		assertThat(seen).hasNullValue();
	}

	@Test
	void shouldPropagateTimeoutException() {
		TimeoutException timeout = new TimeoutException("Expected");
		TestReactiveHealthIndicator indicator = new TestReactiveHealthIndicator(
				(builder, ignored) -> Mono.error(timeout));
		StepVerifier.create(indicator.health(TIMEOUT)).verifyErrorSatisfies((ex) -> assertThat(ex).isSameAs(timeout));
	}

	@Test
	void shouldTurnOtherErrorsIntoDownHealth(CapturedOutput output) {
		TestReactiveHealthIndicator indicator = new TestReactiveHealthIndicator(
				(builder, ignored) -> Mono.error(new IllegalStateException("Test exception")));
		StepVerifier.create(indicator.health(TIMEOUT))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.DOWN))
			.verifyComplete();
		assertThat(output).contains("Test message").contains("Test exception");
	}

	@Test
	void shouldTurnThrownExceptionsIntoDownHealth(CapturedOutput output) {
		TestReactiveHealthIndicator indicator = new TestReactiveHealthIndicator((builder, ignored) -> {
			throw new IllegalStateException("Test exception");
		});
		StepVerifier.create(indicator.health(TIMEOUT))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.DOWN))
			.verifyComplete();
		assertThat(output).contains("Test message").contains("Test exception");
	}

	private static final class TestReactiveHealthIndicator extends AbstractTimeoutAwareReactiveHealthIndicator {

		private final BiFunction<Health.Builder, @Nullable Duration, Mono<Health>> healthCheck;

		private TestReactiveHealthIndicator(BiFunction<Health.Builder, @Nullable Duration, Mono<Health>> healthCheck) {
			this(TimeoutEnforcement.INDICATOR, healthCheck);
		}

		private TestReactiveHealthIndicator(TimeoutEnforcement timeoutEnforcement,
				BiFunction<Health.Builder, @Nullable Duration, Mono<Health>> healthCheck) {
			super(timeoutEnforcement, "Test message");
			this.healthCheck = healthCheck;
		}

		@Override
		protected Mono<Health> doHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
			return this.healthCheck.apply(builder, timeout);
		}

	}

}
