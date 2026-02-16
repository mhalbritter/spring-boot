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
import reactor.test.StepVerifier;

import org.springframework.boot.health.contributor.ExecutorTestSupport.TimeoutEnforcingIndicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link HealthIndicatorAdapter}.
 *
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 */
class HealthIndicatorAdapterTests {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(30);

	private final HealthIndicator delegate = mock(HealthIndicator.class);

	private final HealthIndicatorAdapter adapter = new HealthIndicatorAdapter(this.delegate);

	@Test
	void delegateReturnsHealth() {
		Health status = Health.up().build();
		given(this.delegate.health()).willReturn(status);
		StepVerifier.create(this.adapter.health()).expectNext(status).expectComplete().verify(VERIFY_TIMEOUT);
	}

	@Test
	void delegateThrowError() {
		given(this.delegate.health()).willThrow(new IllegalStateException("Expected"));
		StepVerifier.create(this.adapter.health()).expectError(IllegalStateException.class).verify(VERIFY_TIMEOUT);
	}

	@Test
	void shouldReturnDelegateTimeoutEnforcement() {
		for (TimeoutEnforcement value : TimeoutEnforcement.values()) {
			given(this.delegate.getTimeoutEnforcement()).willReturn(value);
			assertThat(this.adapter.getTimeoutEnforcement()).isEqualTo(value);
		}
	}

	@Test
	void shouldDelegateHealthWithTimeout() throws Exception {
		Health status = Health.up().build();
		given(this.delegate.health(TIMEOUT)).willReturn(status);
		StepVerifier.create(this.adapter.health(TIMEOUT)).expectNext(status).expectComplete().verify(VERIFY_TIMEOUT);
		then(this.delegate).should().health(TIMEOUT);
	}

	@Test
	void shouldDelegateHealthWithTimeoutAndDetails() throws Exception {
		Health status = Health.up().build();
		given(this.delegate.health(TIMEOUT, true)).willReturn(status);
		StepVerifier.create(this.adapter.health(TIMEOUT, true))
			.expectNext(status)
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
		then(this.delegate).should().health(TIMEOUT, true);
	}

	@Test
	void shouldReturnDelegate() {
		assertThat(this.adapter.getDelegate()).isSameAs(this.delegate);
	}

	@Test
	void shouldStripDetailsWhenTheyAreNotIncluded() {
		HealthIndicatorAdapter adapter = new HealthIndicatorAdapter(
				() -> Health.up().withDetail("test", "test").build());
		StepVerifier.create(adapter.health(false)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.UP);
			assertThat(health.getDetails()).isEmpty();
		}).expectComplete().verify(VERIFY_TIMEOUT);
	}

	@Test
	void shouldStripDetailsWhenTheyAreNotIncludedWithTimeout() {
		HealthIndicatorAdapter adapter = new HealthIndicatorAdapter(
				new TimeoutEnforcingIndicator(() -> Health.up().withDetail("test", "test").build()));
		StepVerifier.create(adapter.health(TIMEOUT, false)).assertNext((health) -> {
			assertThat(health.getStatus()).isEqualTo(Status.UP);
			assertThat(health.getDetails()).isEmpty();
		}).expectComplete().verify(VERIFY_TIMEOUT);
	}

	@Test
	void delegateRunsOnTheElasticScheduler() {
		String currentThread = Thread.currentThread().getName();
		HealthIndicatorAdapter adapter = new HealthIndicatorAdapter(
				() -> Health.status(Thread.currentThread().getName().equals(currentThread) ? Status.DOWN : Status.UP)
					.build());
		StepVerifier.create(adapter.health())
			.expectNext(Health.status(Status.UP).build())
			.expectComplete()
			.verify(VERIFY_TIMEOUT);
	}

}
