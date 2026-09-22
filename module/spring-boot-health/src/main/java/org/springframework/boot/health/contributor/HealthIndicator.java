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
 * <p>
 * A configured execution timeout is capped by Spring Boot by default. An implementation
 * which can bound the check with its own client-level timeout should override
 * {@link #getTimeoutEnforcement()} to return {@link TimeoutEnforcement#INDICATOR} and
 * {@link #health(Duration)} to apply it.
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
	 * Returns who caps a configured timeout for this indicator. Must return the same
	 * value for the lifetime of the instance.
	 * @return the timeout enforcement of this indicator
	 * @since 4.2.0
	 */
	default TimeoutEnforcement getTimeoutEnforcement() {
		return TimeoutEnforcement.FRAMEWORK;
	}

	/**
	 * Return an indication of health, bounded by the given timeout. Only called when
	 * {@link #getTimeoutEnforcement()} returns {@link TimeoutEnforcement#INDICATOR},
	 * which requires this method to be overridden. The effective limit may be rounded up
	 * to the client's granularity, but must never be shorter than requested.
	 * @param timeout the timeout
	 * @return the health
	 * @throws UnsupportedOperationException if the indicator doesn't bound the check
	 * itself
	 * @throws TimeoutException if the timeout expired. Implementations must translate a
	 * driver-specific timeout exception into a {@link TimeoutException}
	 * @since 4.2.0
	 */
	default @Nullable Health health(Duration timeout) throws TimeoutException {
		throw new UnsupportedOperationException(
				"'%s' declares TimeoutEnforcement.INDICATOR but doesn't override health(Duration)"
					.formatted(getClass().getName()));
	}

	/**
	 * Return an indication of health, bounded by the given timeout.
	 * @param timeout the timeout
	 * @param includeDetails if details should be included or removed
	 * @return the health
	 * @throws TimeoutException if the timeout expired, see {@link #health(Duration)}
	 * @since 4.2.0
	 */
	default @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
		Health health = health(timeout);
		if (health == null) {
			return null;
		}
		return includeDetails ? health : health.withoutDetails();
	}

}
