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

import org.jspecify.annotations.Nullable;

/**
 * Directly contributes {@link Health} information for specific component or subsystem.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @since 4.0.0
 */
@FunctionalInterface
public non-sealed interface HealthIndicator extends HealthContributor {

	/**
	 * Return an indication of health.
	 * @param includeDetails if details should be included or removed
	 * @return the health
	 */
	default @Nullable Health health(boolean includeDetails) {
		Health health = health();
		if (health == null) {
			return null;
		}
		return includeDetails ? health : health.withoutDetails();
	}

	/**
	 * Return an indication of health.
	 * @return the health
	 */
	@Nullable Health health();

	/**
	 * Returns how this indicator participates when a timeout is configured (see
	 * {@link HealthIndicatorExecutor}).
	 * <ul>
	 * <li>{@link TimeoutSupport#NONE} &mdash; the configured timeout is not enforced; a
	 * warning is logged once per indicator name if a timeout is configured anyway.</li>
	 * <li>{@link TimeoutSupport#NATIVE} &mdash; {@link #health(Duration)} (or
	 * {@link #health(Duration, boolean)}) is called so the indicator can apply the limit
	 * with stack-appropriate APIs.</li>
	 * <li>{@link TimeoutSupport#INTERRUPTION} &mdash; {@link #health()} (or
	 * {@link #health(boolean)}) is run on another thread and
	 * {@link java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)}
	 * enforces the limit; on timeout the task is cancelled with interruption.</li>
	 * </ul>
	 * @return the timeout support of that indicator
	 * @since 4.1.0
	 */
	default TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NONE;
	}

	/**
	 * Return an indication of health, respecting the given timeout. This method will only
	 * be called if {@link #getTimeoutSupport()} returns {@link TimeoutSupport#NATIVE}.
	 * @param timeout the timeout
	 * @return the health
	 * @throws TimeoutException if a timeout occurred
	 * @since 4.1.0
	 */
	default @Nullable Health health(Duration timeout) throws TimeoutException {
		throw new UnsupportedOperationException("Timeout is not supported");
	}

	/**
	 * Return an indication of health, respecting the given timeout. This method will only
	 * be called if {@link #getTimeoutSupport()} returns {@link TimeoutSupport#NATIVE}.
	 * @param includeDetails if details should be included or removed
	 * @param timeout the timeout
	 * @return the health
	 * @throws TimeoutException if a timeout occurred
	 * @since 4.1.0
	 */
	default @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
		Health health = health(timeout);
		if (health == null) {
			return null;
		}
		return includeDetails ? health : health.withoutDetails();
	}

}
