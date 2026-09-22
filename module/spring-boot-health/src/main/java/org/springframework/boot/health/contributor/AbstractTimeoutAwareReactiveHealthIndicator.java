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
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * Base {@link ReactiveHealthIndicator} implementation for indicators which use a
 * configured timeout to bound their check at the client.
 * <p>
 * Implement {@link #doHealthCheck(Health.Builder, Duration)}; {@code timeout} is
 * {@code null} when no timeout is configured. An indicator which can bound the whole
 * check declares {@link TimeoutEnforcement#INDICATOR} and is not capped by Spring Boot;
 * one which can only bound part of it declares {@link TimeoutEnforcement#FRAMEWORK} and
 * stays capped.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public abstract class AbstractTimeoutAwareReactiveHealthIndicator extends AbstractReactiveHealthIndicator {

	private final TimeoutEnforcement timeoutEnforcement;

	/**
	 * Create a new {@link AbstractTimeoutAwareReactiveHealthIndicator} instance with a
	 * default {@code healthCheckFailedMessage}.
	 * @param timeoutEnforcement who caps a configured timeout
	 */
	protected AbstractTimeoutAwareReactiveHealthIndicator(TimeoutEnforcement timeoutEnforcement) {
		super();
		Assert.notNull(timeoutEnforcement, "'timeoutEnforcement' must not be null");
		this.timeoutEnforcement = timeoutEnforcement;
	}

	/**
	 * Create a new {@link AbstractTimeoutAwareReactiveHealthIndicator} instance with a
	 * specific message to log when the health check fails.
	 * @param timeoutEnforcement who caps a configured timeout
	 * @param healthCheckFailedMessage the message to log on health check failure
	 */
	protected AbstractTimeoutAwareReactiveHealthIndicator(TimeoutEnforcement timeoutEnforcement,
			@Nullable String healthCheckFailedMessage) {
		super(healthCheckFailedMessage);
		Assert.notNull(timeoutEnforcement, "'timeoutEnforcement' must not be null");
		this.timeoutEnforcement = timeoutEnforcement;
	}

	/**
	 * Create a new {@link AbstractTimeoutAwareReactiveHealthIndicator} instance with a
	 * specific message to log when the health check fails.
	 * @param timeoutEnforcement who caps a configured timeout
	 * @param healthCheckFailedMessage the message to log on health check failure
	 */
	protected AbstractTimeoutAwareReactiveHealthIndicator(TimeoutEnforcement timeoutEnforcement,
			Function<Throwable, @Nullable String> healthCheckFailedMessage) {
		super(healthCheckFailedMessage);
		Assert.notNull(timeoutEnforcement, "'timeoutEnforcement' must not be null");
		this.timeoutEnforcement = timeoutEnforcement;
	}

	@Override
	public final TimeoutEnforcement getTimeoutEnforcement() {
		return this.timeoutEnforcement;
	}

	@Override
	protected final Mono<Health> doHealthCheck(Health.Builder builder) {
		return doHealthCheck(builder, null);
	}

	@Override
	public final Mono<Health> health(Duration timeout) {
		try {
			Health.Builder builder = new Health.Builder();
			Mono<Health> result = doHealthCheck(builder, timeout)
				.onErrorResume((ex) -> !(ex instanceof TimeoutException), this::handleFailure);
			return result.doOnNext((health) -> logExceptionIfPresent(builder.getException()));
		}
		catch (Exception ex) {
			return handleFailure(ex);
		}
	}

	/**
	 * Actual health check logic. If an error occurs in the pipeline, it will be handled
	 * automatically, except for a {@link TimeoutException} which is propagated, see
	 * {@link ReactiveHealthIndicator#health(Duration)}.
	 * @param builder the {@link Health.Builder} to report health status and details
	 * @param timeout the timeout to apply, or {@code null} if nothing is expected to be
	 * enforced
	 * @return a {@link Mono} that provides the {@link Health}
	 */
	protected abstract Mono<Health> doHealthCheck(Health.Builder builder, @Nullable Duration timeout);

}
