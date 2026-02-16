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
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ReactiveHealthIndicatorAdapter}.
 *
 * @author Phillip Webb
 * @author Moritz Halbritter
 */
class ReactiveHealthIndicatorAdapterTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final ReactiveHealthIndicator delegate = mock(ReactiveHealthIndicator.class);

	private final ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(this.delegate);

	@Test
	void getHealthReturnsDetails() {
		given(this.delegate.health()).willReturn(Mono.just(Health.up().withDetail("test", "test").build()));
		Health health = this.adapter.health();
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("test", "test");
	}

	@Test
	void getHealthWithoutDetailsReturnsHealth() {
		ReactiveHealthIndicatorAdapter adapter = new ReactiveHealthIndicatorAdapter(
				() -> Mono.just(Health.up().withDetail("test", "test").build()));
		Health health = adapter.health(false);
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void shouldReturnDelegateTimeoutEnforcement() {
		for (TimeoutEnforcement value : TimeoutEnforcement.values()) {
			given(this.delegate.getTimeoutEnforcement()).willReturn(value);
			assertThat(this.adapter.getTimeoutEnforcement()).isEqualTo(value);
		}
	}

	@Test
	void shouldDelegateHealthWithTimeout() throws TimeoutException {
		Health status = Health.up().build();
		given(this.delegate.health(TIMEOUT)).willReturn(Mono.just(status));
		assertThat(this.adapter.health(TIMEOUT)).isEqualTo(status);
		then(this.delegate).should().health(TIMEOUT);
	}

	@Test
	void shouldDelegateHealthWithTimeoutAndDetails() throws TimeoutException {
		Health status = Health.up().build();
		given(this.delegate.health(TIMEOUT, true)).willReturn(Mono.just(status));
		assertThat(this.adapter.health(TIMEOUT, true)).isEqualTo(status);
		then(this.delegate).should().health(TIMEOUT, true);
	}

	@Test
	void shouldUnwrapTimeoutException() {
		given(this.delegate.health(TIMEOUT)).willReturn(Mono.error(new TimeoutException("Expected")));
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> this.adapter.health(TIMEOUT));
	}

	@Test
	void shouldUnwrapTimeoutExceptionFromCompletionException() {
		given(this.delegate.health(TIMEOUT))
			.willReturn(Mono.error(new CompletionException(new TimeoutException("Expected"))));
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> this.adapter.health(TIMEOUT));
	}

	@Test
	void shouldRethrowExceptionWhichIsNotATimeout() {
		IllegalStateException failure = new IllegalStateException("Expected");
		given(this.delegate.health(TIMEOUT)).willReturn(Mono.error(failure));
		assertThatIllegalStateException().isThrownBy(() -> this.adapter.health(TIMEOUT)).isSameAs(failure);
	}

	@Test
	void shouldReturnNullWhenDelegateIsEmpty() throws TimeoutException {
		given(this.delegate.health(TIMEOUT)).willReturn(Mono.empty());
		assertThat(this.adapter.health(TIMEOUT)).isNull();
	}

}
