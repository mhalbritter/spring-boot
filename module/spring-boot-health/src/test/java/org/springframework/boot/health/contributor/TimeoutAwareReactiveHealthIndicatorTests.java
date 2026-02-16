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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TimeoutAwareReactiveHealthIndicator}.
 *
 * @author Moritz Halbritter
 */
class TimeoutAwareReactiveHealthIndicatorTests {

	private final TimeoutAwareReactiveHealthIndicator indicator = new TimeoutAwareReactiveHealthIndicator() {

		@Override
		public Mono<Health> health() {
			return Mono.just(Health.up().withDetail("spring", "boot").build());
		}

		@Override
		public Mono<Health> health(Duration timeout) {
			return Mono.just(Health.up().withDetail("spring", "boot").withDetail("timeout", timeout).build());
		}

	};

	@Test
	void shouldAdaptToTimeoutAwareHealthIndicator() throws TimeoutException {
		TimeoutAwareHealthIndicator adapted = this.indicator.asHealthContributor();
		Health health = adapted.health(Duration.ofSeconds(1));
		assertThat(health).isNotNull();
		assertThat(health.getDetails()).containsEntry("timeout", Duration.ofSeconds(1));
	}

	@Test
	void shouldReturnHealthWithDetailsWhenTimeoutIsGivenAndIncludeDetailsIsTrue() {
		Health health = this.indicator.health(Duration.ofSeconds(1), true).block();
		assertThat(health).isNotNull();
		assertThat(health.getDetails()).containsEntry("spring", "boot").containsEntry("timeout", Duration.ofSeconds(1));
	}

	@Test
	void shouldReturnHealthWithoutDetailsWhenTimeoutIsGivenAndIncludeDetailsIsFalse() {
		Health health = this.indicator.health(Duration.ofSeconds(1), false).block();
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).isEmpty();
	}

}
