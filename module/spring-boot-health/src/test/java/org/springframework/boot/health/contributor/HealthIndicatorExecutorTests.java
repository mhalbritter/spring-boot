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
import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link HealthIndicatorExecutor}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class HealthIndicatorExecutorTests {

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
	void executeWithoutTimeoutCallsHealthDirectly() {
		Health result = this.executor.execute(() -> Health.up().build(), "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void executeWithNullEnvironmentCallsHealthDirectly() throws Exception {
		HealthIndicatorExecutor executor = new HealthIndicatorExecutor(null);
		try {
			Health result = executor.execute(() -> Health.up().build(), "test", true).join();
			assertThat(result).isNotNull();
			assertThat(result.getStatus()).isEqualTo(Status.UP);
		}
		finally {
			executor.destroy();
		}
	}

	@Test
	void executeWithIncludeDetailsFalseRemovesDetails() {
		Health result = this.executor.execute(() -> Health.up().withDetail("key", "value").build(), "test", false)
			.join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
		assertThat(result.getDetails()).isEmpty();
	}

	@Test
	void executeWithTimeoutReturnsHealthWhenNotExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		Health result = this.executor.execute(() -> Health.up().build(), "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void executeWithTimeoutReturnsDownAndInterruptsThreadWhenExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofMillis(50));
		AtomicBoolean interrupted = new AtomicBoolean();
		HealthIndicator slowIndicator = HealthContributorTestIndicators
			.blockingSleepInterruption(Duration.ofMillis(500), interrupted);
		Health result = this.executor.execute(slowIndicator, "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
		Awaitility.await().atMost(Duration.ofSeconds(1)).untilAtomic(interrupted, Matchers.equalTo(true));
	}

	@Test
	void executeWithDefaultTimeoutReturnsDownWhenExceeded() {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		HealthIndicator slowIndicator = HealthContributorTestIndicators
			.blockingSleepInterruption(Duration.ofMillis(500));
		Health result = this.executor.execute(slowIndicator, "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void executeIndicatorTimeoutTakesPrecedenceOverDefaultTimeout() {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		HealthIndicator slightlySlowIndicator = HealthContributorTestIndicators
			.blockingSleepInterruption(Duration.ofMillis(200));
		Health result = this.executor.execute(slightlySlowIndicator, "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void executeUsesNativeTimeoutWhenSupportedAndExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofMillis(50));
		HealthIndicator indicator = new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NATIVE;
			}

			@Override
			public @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
				throw new TimeoutException("exceeded");
			}

			@Override
			public Health health() {
				fail("Did not expect health() to be called");
				return Health.up().build();
			}
		};
		Health result = this.executor.execute(indicator, "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void executeUsesNativeTimeoutWhenSupportedAndNotExceeded() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		AtomicBoolean healthWithTimeoutCalled = new AtomicBoolean();
		HealthIndicator indicator = new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NATIVE;
			}

			@Override
			public Health health(Duration timeout, boolean includeDetails) {
				healthWithTimeoutCalled.set(true);
				return Health.up().build();
			}

			@Override
			public Health health() {
				fail("Did not expect health() to be called");
				return Health.up().build();
			}
		};
		Health result = this.executor.execute(indicator, "test", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
		assertThat(healthWithTimeoutCalled).isTrue();
	}

	@Test
	void executeWithNestedIndicatorNameMapsSlashToDot() {
		this.environment.setProperty("management.health.datasource.primary.timeout", Duration.ofMillis(50));
		HealthIndicator slowIndicator = HealthContributorTestIndicators
			.blockingSleepInterruption(Duration.ofMillis(500));
		Health result = this.executor.execute(slowIndicator, "datasource/primary", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void executeWithUppercaseIndicatorNameIsLowercased() {
		this.environment.setProperty("management.health.db.timeout", Duration.ofMillis(50));
		HealthIndicator slowIndicator = HealthContributorTestIndicators
			.blockingSleepInterruption(Duration.ofMillis(500));
		Health result = this.executor.execute(slowIndicator, "DB", true).join();
		assertThat(result).isNotNull();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails()).containsEntry("reason", "timeout");
	}

	@Test
	void executeAfterDestroyThrowsIllegalStateException() throws Exception {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		this.executor.destroy();
		HealthIndicator indicator = HealthContributorTestIndicators.interruptionHealthIndicator(Health.up()::build);
		assertThatIllegalStateException().isThrownBy(() -> this.executor.execute(indicator, "test", true));
	}

	@Test
	void executeWithTimeoutWhenIndicatorDoesNotSupportTimeoutLogsWarningOnce(CapturedOutput output) {
		this.environment.setProperty("management.health.no-timeout-support.timeout", Duration.ofSeconds(2));
		HealthIndicator indicator = new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Health health() {
				return Health.up().build();
			}
		};
		assertThat(this.executor.execute(indicator, "no-timeout-support", true).join().getStatus())
			.isEqualTo(Status.UP);
		assertThat(this.executor.execute(indicator, "no-timeout-support", true).join().getStatus())
			.isEqualTo(Status.UP);
		assertThat(output).containsOnlyOnce(
				"Health indicator no-timeout-support doesn't support timeout, but a timeout of PT2S has been configured");
	}

	@Test
	void executeWithTimeoutWhenIndicatorDoesNotSupportTimeoutLogsOncePerIndicatorName(CapturedOutput output) {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofMillis(50));
		HealthIndicator first = new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Health health() {
				return Health.up().build();
			}
		};
		HealthIndicator second = new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.NONE;
			}

			@Override
			public Health health() {
				return Health.up().build();
			}
		};
		assertThat(this.executor.execute(first, "first-none", true).join().getStatus()).isEqualTo(Status.UP);
		assertThat(this.executor.execute(second, "second-none", true).join().getStatus()).isEqualTo(Status.UP);
		assertThat(output).containsOnlyOnce("Health indicator first-none doesn't support timeout");
		assertThat(output).containsOnlyOnce("Health indicator second-none doesn't support timeout");
	}

}
