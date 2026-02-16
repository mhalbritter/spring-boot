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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReactiveHealthIndicatorAdapter}.
 *
 * @author Phillip Webb
 */
class ReactiveHealthIndicatorAdapterTests {

	@Test
	void getHealthReturnsDetails() {
		ReactiveHealthIndicator reactiveHealthIndicator = () -> Mono
			.just(Health.up().withDetail("test", "test").build());
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(reactiveHealthIndicator);
		Health health = adapter.health();
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("test", "test");
	}

	@Test
	void getHealthWithoutDetailsReturnsHealth() {
		ReactiveHealthIndicator reactiveHealthIndicator = () -> Mono
			.just(Health.up().withDetail("test", "test").build());
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(reactiveHealthIndicator);
		Health health = adapter.health(false);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void shouldReturnDelegateTimeout() {
		ReactiveHealthIndicator delegate = mock(ReactiveHealthIndicator.class);
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(delegate);
		for (TimeoutSupport value : TimeoutSupport.values()) {
			given(delegate.getTimeoutSupport()).willReturn(value);
			assertThat(adapter.getTimeoutSupport()).isEqualTo(value);
		}
	}

	@Test
	void shouldDelegateHealthWithTimeout() throws TimeoutException {
		ReactiveHealthIndicator delegate = mock(ReactiveHealthIndicator.class);
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(delegate);
		Health status = Health.up().build();
		Duration timeout = Duration.ofSeconds(5);
		given(delegate.health(timeout)).willReturn(Mono.just(status));
		adapter.health(timeout);
		then(delegate).should().health(timeout);
	}

	@Test
	void shouldDelegateHealthWithTimeoutAndDetails() throws TimeoutException {
		ReactiveHealthIndicator delegate = mock(ReactiveHealthIndicator.class);
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(delegate);
		Health status = Health.up().build();
		Duration timeout = Duration.ofSeconds(5);
		given(delegate.health(timeout, true)).willReturn(Mono.just(status));
		adapter.health(timeout, true);
		then(delegate).should().health(timeout, true);
	}

}
