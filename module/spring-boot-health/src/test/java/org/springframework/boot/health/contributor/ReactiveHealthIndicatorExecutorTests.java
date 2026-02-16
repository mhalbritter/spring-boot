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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

	private MockEnvironment environment;

	private ReactiveHealthIndicatorExecutor executor;

	@BeforeEach
	void setUp() {
		this.environment = new MockEnvironment();
		this.executor = new ReactiveHealthIndicatorExecutor(this.environment);
	}

	@Test
	void executeWithoutTimeoutCallsHealthDirectly() {
		StepVerifier.create(this.executor.execute(() -> Mono.just(Health.up().build()), "test", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithNullEnvironmentCallsHealthDirectly() {
		ReactiveHealthIndicatorExecutor executor = new ReactiveHealthIndicatorExecutor(null);
		StepVerifier.create(executor.execute(() -> Mono.just(Health.up().build()), "test", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithIncludeDetailsFalseRemovesDetails() {
		StepVerifier
			.create(this.executor.execute(() -> Mono.just(Health.up().withDetail("key", "value").build()), "test",
					false))
			.assertNext((health) -> {
				assertThat(health.getStatus()).isEqualTo(Status.UP);
				assertThat(health.getDetails()).isEmpty();
			})
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithTimeoutReturnsHealthWhenNotExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		StepVerifier.create(this.executor.execute(() -> Mono.just(Health.up().build()), "test", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithTimeoutReturnsDownWhenExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator slowIndicator = HealthContributorTestIndicators
			.delayedInterruption(Duration.ofMillis(500));
		StepVerifier.create(this.executor.execute(slowIndicator, "test", true)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}).expectComplete().verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithDefaultTimeoutReturnsDownWhenExceeded() {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator slowIndicator = HealthContributorTestIndicators
			.delayedInterruption(Duration.ofMillis(500));
		StepVerifier.create(this.executor.execute(slowIndicator, "test", true)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}).expectComplete().verify(Duration.ofSeconds(5));
	}

	@Test
	void executeIndicatorTimeoutTakesPrecedenceOverDefaultTimeout() {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		ReactiveHealthIndicator slightlySlowIndicator = HealthContributorTestIndicators
			.delayedInterruption(Duration.ofMillis(200));
		StepVerifier.create(this.executor.execute(slightlySlowIndicator, "test", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
	}

	@Test
	void executeUsesNativeTimeoutWhenSupportedAndExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator indicator = new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NATIVE;
			}

			@Override
			public Mono<Health> health(Duration timeout, boolean includeDetails) {
				return Mono.just(Health.down().withDetail("reason", "timeout").build());
			}

			@Override
			public Mono<Health> health() {
				fail("Did not expect health() to be called");
				return Mono.just(Health.up().build());
			}
		};
		StepVerifier.create(this.executor.execute(indicator, "test", true)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}).expectComplete().verify(Duration.ofSeconds(5));
	}

	@Test
	void executeUsesNativeTimeoutWhenSupportedAndNotExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		AtomicBoolean healthWithTimeoutCalled = new AtomicBoolean();
		ReactiveHealthIndicator indicator = new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NATIVE;
			}

			@Override
			public Mono<Health> health(Duration timeout, boolean includeDetails) {
				healthWithTimeoutCalled.set(true);
				return Mono.just(Health.up().build());
			}

			@Override
			public Mono<Health> health() {
				fail("Did not expect health() to be called");
				return Mono.just(Health.up().build());
			}
		};
		StepVerifier.create(this.executor.execute(indicator, "test", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		assertThat(healthWithTimeoutCalled).isTrue();
	}

	@Test
	void executeWithNestedIndicatorNameMapsSlashToDot() {
		this.environment.setProperty("management.health.datasource.primary.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator slowIndicator = HealthContributorTestIndicators
			.delayedInterruption(Duration.ofMillis(500));
		StepVerifier.create(this.executor.execute(slowIndicator, "datasource/primary", true)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}).expectComplete().verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithUppercaseIndicatorNameIsLowercased() {
		this.environment.setProperty("management.health.db.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator slowIndicator = HealthContributorTestIndicators
			.delayedInterruption(Duration.ofMillis(500));
		StepVerifier.create(this.executor.execute(slowIndicator, "DB", true)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
		}).expectComplete().verify(Duration.ofSeconds(5));
	}

	@Test
	void executeWithTimeoutWhenIndicatorDoesNotSupportTimeoutLogsWarningOnce(CapturedOutput output) {
		this.environment.setProperty("management.health.reactive-warn-once.timeout", Duration.ofSeconds(2));
		ReactiveHealthIndicator indicator = new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Mono<Health> health() {
				return Mono.just(Health.up().build());
			}
		};
		StepVerifier.create(this.executor.execute(indicator, "reactive-warn-once", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		StepVerifier.create(this.executor.execute(indicator, "reactive-warn-once", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		assertThat(output).containsOnlyOnce("Health indicator reactive-warn-once doesn't support timeout");
	}

	@Test
	void executeWithTimeoutWhenIndicatorDoesNotSupportTimeoutLogsOncePerIndicatorName(CapturedOutput output) {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		ReactiveHealthIndicator first = new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Mono<Health> health() {
				return Mono.just(Health.up().build());
			}
		};
		ReactiveHealthIndicator second = new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Mono<Health> health() {
				return Mono.just(Health.up().build());
			}
		};
		StepVerifier.create(this.executor.execute(first, "reactive-first-none", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		StepVerifier.create(this.executor.execute(second, "reactive-second-none", true))
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(5));
		assertThat(output).containsOnlyOnce("Health indicator reactive-first-none doesn't support timeout");
		assertThat(output).containsOnlyOnce("Health indicator reactive-second-none doesn't support timeout");
	}

}
