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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link AbstractTimeoutAwareHealthIndicator}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class AbstractTimeoutAwareHealthIndicatorTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	@Test
	void shouldEnforceTimeoutItself() {
		assertThat(new TestHealthIndicator((builder, timeout) -> builder.up()).getTimeoutEnforcement())
			.isEqualTo(TimeoutEnforcement.INDICATOR);
	}

	@Test
	void shouldPassTimeoutToHealthCheck() throws TimeoutException {
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>();
		TestHealthIndicator indicator = new TestHealthIndicator((builder, timeout) -> {
			seen.set(timeout);
			builder.up();
		});
		assertThat(indicator.health(TIMEOUT).getStatus()).isEqualTo(Status.UP);
		assertThat(seen).hasValue(TIMEOUT);
	}

	@Test
	void shouldPassNullTimeoutToHealthCheckWhenNoTimeoutIsConfigured() {
		AtomicReference<@Nullable Duration> seen = new AtomicReference<>(TIMEOUT);
		TestHealthIndicator indicator = new TestHealthIndicator((builder, timeout) -> {
			seen.set(timeout);
			builder.up();
		});
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
		assertThat(seen).hasNullValue();
	}

	@Test
	void shouldPropagateTimeoutException() {
		TimeoutException timeout = new TimeoutException("Expected");
		TestHealthIndicator indicator = new TestHealthIndicator((builder, ignored) -> {
			throw timeout;
		});
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> indicator.health(TIMEOUT)).isSameAs(timeout);
	}

	@Test
	void shouldTurnOtherExceptionsIntoDownHealth(CapturedOutput output) throws TimeoutException {
		TestHealthIndicator indicator = new TestHealthIndicator((builder, ignored) -> {
			throw new IllegalStateException("Test exception");
		});
		Health health = indicator.health(TIMEOUT);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(output).contains("Test message").contains("Test exception");
	}

	private static final class TestHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

		private final HealthCheck healthCheck;

		private TestHealthIndicator(HealthCheck healthCheck) {
			super("Test message");
			this.healthCheck = healthCheck;
		}

		@Override
		protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception {
			this.healthCheck.run(builder, timeout);
		}

	}

	private interface HealthCheck {

		void run(Health.Builder builder, @Nullable Duration timeout) throws Exception;

	}

}
