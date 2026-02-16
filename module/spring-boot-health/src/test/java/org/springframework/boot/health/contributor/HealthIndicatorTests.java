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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link HealthIndicator}.
 *
 * @author Phillip Webb
 * @author Moritz Halbritter
 */
class HealthIndicatorTests {

	private final HealthIndicator indicator = () -> Health.up().withDetail("spring", "boot").build();

	@Test
	void getHealthWhenIncludeDetailsIsTrueReturnsHealthWithDetails() {
		Health health = this.indicator.health(true);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("spring", "boot");
	}

	@Test
	void getHealthWhenIncludeDetailsIsFalseReturnsHealthWithoutDetails() {
		Health health = this.indicator.health(false);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void getTimeoutEnforcementDefaultsToFramework() {
		assertThat(this.indicator.getTimeoutEnforcement()).isEqualTo(TimeoutEnforcement.FRAMEWORK);
	}

	@Test
	void getHealthWithTimeoutThrowsWhenNotOverridden() {
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> this.indicator.health(Duration.ofSeconds(1)))
			.withMessageContaining("doesn't override health(Duration)");
		assertThatExceptionOfType(UnsupportedOperationException.class)
			.isThrownBy(() -> this.indicator.health(Duration.ofSeconds(1), true));
	}

}
