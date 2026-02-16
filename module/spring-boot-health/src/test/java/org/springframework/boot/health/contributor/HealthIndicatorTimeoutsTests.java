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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.HealthIndicatorTimeouts.InvalidTimeoutException;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link HealthIndicatorTimeouts}.
 *
 * @author Moritz Halbritter
 */
class HealthIndicatorTimeoutsTests {

	private MockEnvironment environment;

	private HealthIndicatorTimeouts timeouts;

	@BeforeEach
	void setUp() {
		this.environment = new MockEnvironment();
		this.timeouts = new HealthIndicatorTimeouts(this.environment);
	}

	@Test
	void shouldReturnNullWithoutPropertySources() {
		HealthIndicatorTimeouts timeouts = new HealthIndicatorTimeouts(
				new PropertySourcesPropertyResolver(new MutablePropertySources()));
		assertThat(timeouts.get("test")).isNull();
	}

	@Test
	void shouldReturnNullWhenNoTimeoutIsConfigured() {
		assertThat(this.timeouts.get("test")).isNull();
	}

	@Test
	void shouldReturnTimeoutOfIndicator() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		assertThat(this.timeouts.get("test")).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void shouldFallBackToDefaultTimeout() {
		this.environment.setProperty("management.health.defaults.timeout", Duration.ofSeconds(3));
		assertThat(this.timeouts.get("test")).isEqualTo(Duration.ofSeconds(3));
	}

	@Test
	void shouldUseLeafPathOfComposite() {
		this.environment.setProperty("management.health.composite.leaf.timeout", Duration.ofSeconds(4));
		assertThat(this.timeouts.get("composite/leaf")).isEqualTo(Duration.ofSeconds(4));
	}

	@Test
	void shouldLowercaseIndicatorName() {
		this.environment.setProperty("management.health.db.timeout", Duration.ofSeconds(5));
		assertThat(this.timeouts.get("DB")).isEqualTo(Duration.ofSeconds(5));
	}

	@Test
	void shouldRejectTimeoutWhichIsNotPositive() {
		this.environment.setProperty("management.health.test.timeout", Duration.ZERO);
		assertThatExceptionOfType(InvalidTimeoutException.class).isThrownBy(() -> this.timeouts.get("test"))
			.withMessageContaining("Timeout configured in property 'management.health.test.timeout' must be positive");
	}

	@Test
	void shouldRejectTimeoutWhichCannotBeRead() {
		this.environment.setProperty("management.health.test.timeout", "not-a-duration");
		assertThatExceptionOfType(InvalidTimeoutException.class).isThrownBy(() -> this.timeouts.get("test"))
			.withMessageContaining("Timeout configured in property 'management.health.test.timeout' cannot be read");
	}

	@Test
	void shouldReportRejectedTimeoutOnEveryCall() {
		this.environment.setProperty("management.health.test.timeout", Duration.ZERO);
		assertThatIllegalStateException().isThrownBy(() -> this.timeouts.get("test"));
		assertThatIllegalStateException().isThrownBy(() -> this.timeouts.get("test"));
	}

	@Test
	void shouldResolveTimeoutOnlyOnce() {
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		assertThat(this.timeouts.get("test")).isEqualTo(Duration.ofSeconds(2));
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(9));
		assertThat(this.timeouts.get("test")).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void shouldRememberThatAnIndicatorHasNoTimeout() {
		assertThat(this.timeouts.get("test")).isNull();
		this.environment.setProperty("management.health.test.timeout", Duration.ofSeconds(2));
		assertThat(this.timeouts.get("test")).isNull();
	}

}
