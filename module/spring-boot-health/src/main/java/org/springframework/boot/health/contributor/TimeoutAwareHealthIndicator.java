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
 * A {@link HealthIndicator} which is handed a configured execution timeout, so that it
 * can bound its check with a client-level timeout.
 * <p>
 * A configured timeout is capped by Spring Boot unless {@link #getTimeoutEnforcement()}
 * returns {@link TimeoutEnforcement#INDICATOR}. An indicator which does not implement
 * this interface is always capped by Spring Boot.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 * @see AbstractTimeoutAwareHealthIndicator
 */
public interface TimeoutAwareHealthIndicator extends HealthIndicator {

	/**
	 * Returns who caps a configured timeout for this indicator. Must return the same
	 * value for the lifetime of the instance.
	 * @return the timeout enforcement of this indicator
	 */
	default TimeoutEnforcement getTimeoutEnforcement() {
		return TimeoutEnforcement.FRAMEWORK;
	}

	/**
	 * Return an indication of health, bounded by the given timeout. Called by the default
	 * implementation of {@link #health(Duration, boolean)}. The effective limit may be
	 * rounded up to the client's granularity, but must never be shorter than requested.
	 * @param timeout the timeout
	 * @return the health
	 * @throws TimeoutException if the timeout expired. Implementations must translate a
	 * driver-specific timeout exception into a {@link TimeoutException}: it is the only
	 * exception mapped to {@link Status#DOWN} with a {@code reason: "timeout"} detail,
	 * any other maps to an ordinary {@link Status#DOWN}.
	 */
	@Nullable Health health(Duration timeout) throws TimeoutException;

	/**
	 * Return an indication of health, bounded by the given timeout. Called instead of
	 * {@link #health(boolean)} whenever a timeout is configured.
	 * <p>
	 * The default implementation calls {@link #health(Duration)} and removes the details
	 * itself, leaving an override of {@link #health(boolean)} unused. An implementation
	 * which overrides {@link #health(boolean)} has to override this method as well.
	 * @param timeout the timeout
	 * @param includeDetails if details should be included or removed
	 * @return the health
	 * @throws TimeoutException if the timeout expired, see {@link #health(Duration)}
	 */
	default @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
		Health health = health(timeout);
		if (health == null) {
			return null;
		}
		return includeDetails ? health : health.withoutDetails();
	}

}
