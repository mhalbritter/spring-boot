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

/**
 * Base {@link HealthIndicator} implementation for indicators which enforce a configured
 * timeout themselves ({@link TimeoutEnforcement#INDICATOR}), encapsulating creation of
 * {@link Health} instance and error handling.
 * <p>
 * Implement {@link #doHealthCheck(Health.Builder, Duration)}; {@code timeout} is
 * {@code null} when no timeout is configured and non-null otherwise.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public abstract class AbstractTimeoutEnforcingHealthIndicator extends AbstractHealthIndicator {

	/**
	 * Create a new {@link AbstractTimeoutEnforcingHealthIndicator} instance with a
	 * default {@code healthCheckFailedMessage}.
	 */
	protected AbstractTimeoutEnforcingHealthIndicator() {
		super();
	}

	/**
	 * Create a new {@link AbstractTimeoutEnforcingHealthIndicator} instance with a
	 * specific message to log when the health check fails.
	 * @param healthCheckFailedMessage the message to log on health check failure
	 */
	protected AbstractTimeoutEnforcingHealthIndicator(@Nullable String healthCheckFailedMessage) {
		super(healthCheckFailedMessage);
	}

	/**
	 * Create a new {@link AbstractTimeoutEnforcingHealthIndicator} instance with a
	 * specific message to log when the health check fails.
	 * @param healthCheckFailedMessage the message to log on health check failure
	 */
	protected AbstractTimeoutEnforcingHealthIndicator(Function<Exception, @Nullable String> healthCheckFailedMessage) {
		super(healthCheckFailedMessage);
	}

	@Override
	public final TimeoutEnforcement getTimeoutEnforcement() {
		return TimeoutEnforcement.INDICATOR;
	}

	@Override
	protected final void doHealthCheck(Health.Builder builder) throws Exception {
		doHealthCheck(builder, null);
	}

	@Override
	public final Health health(Duration timeout) throws TimeoutException {
		Health.Builder builder = new Health.Builder();
		try {
			doHealthCheck(builder, timeout);
		}
		catch (TimeoutException ex) {
			throw ex;
		}
		catch (Exception ex) {
			builder.down(ex);
		}
		logExceptionIfPresent(builder.getException());
		return builder.build();
	}

	/**
	 * Actual health check logic.
	 * @param builder the {@link Health.Builder} to report health status and details
	 * @param timeout the timeout to apply, or {@code null} if nothing is expected to be
	 * enforced
	 * @throws Exception any {@link Exception} that should create a {@link Status#DOWN}
	 * system status
	 * @throws TimeoutException if the timeout expired, see
	 * {@link HealthIndicator#health(Duration)}
	 */
	protected abstract void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception;

}
