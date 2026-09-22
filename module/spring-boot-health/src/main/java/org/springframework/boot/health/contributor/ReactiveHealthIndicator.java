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

import reactor.core.publisher.Mono;

/**
 * Directly contributes {@link Health} information for specific reactive component or
 * subsystem.
 * <p>
 * This is non-blocking contract that is meant to be used in a reactive application. See
 * {@link HealthIndicator} for the traditional contract.
 * <p>
 * A configured execution timeout is capped by Spring Boot by default and handed to
 * {@link #health(Duration)}, which an implementation can override to bound the check with
 * its own client-level timeout. An implementation which bounds the whole check that way
 * should also override {@link #getTimeoutEnforcement()} to return
 * {@link TimeoutEnforcement#INDICATOR}.
 *
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @since 4.0.0
 * @see HealthIndicator
 * @see TimeoutEnforcement
 */
@FunctionalInterface
public non-sealed interface ReactiveHealthIndicator extends ReactiveHealthContributor {

	@Override
	default HealthIndicator asHealthContributor() {
		return new ReactiveHealthIndicatorAdapter(this);
	}

	/**
	 * Provide the indicator of health.
	 * @param includeDetails if details should be included or removed
	 * @return a {@link Mono} that provides the {@link Health}
	 */
	default Mono<Health> health(boolean includeDetails) {
		Mono<Health> health = health();
		return includeDetails ? health : health.map(Health::withoutDetails);
	}

	/**
	 * Provide the indicator of health.
	 * @return a {@link Mono} that provides the {@link Health}
	 */
	Mono<Health> health();

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
	 * Provide the indicator of health, bounded by the given timeout. Called whenever a
	 * timeout is configured. The effective limit may be rounded up to the client's
	 * granularity, but must never be shorter than requested.
	 * <p>
	 * The default implementation ignores the timeout and calls {@link #health()}, which
	 * leaves capping the check to Spring Boot.
	 * @param timeout the timeout
	 * @return a {@link Mono} that provides the {@link Health}, or signals a
	 * {@link TimeoutException} if the timeout expired. Implementations must translate a
	 * driver-specific timeout exception into a {@link TimeoutException}: it is the only
	 * exception mapped to {@link Status#DOWN} with a {@code reason: "timeout"} detail,
	 * any other maps to an ordinary {@link Status#DOWN}.
	 * @since 4.2.0
	 */
	default Mono<Health> health(Duration timeout) {
		return health();
	}

	/**
	 * Provide the indicator of health, bounded by the given timeout.
	 * <p>
	 * The default implementation calls {@link #health(Duration)} and removes the details
	 * itself, leaving an override of {@link #health(boolean)} unused. An implementation
	 * which overrides both single-argument variants has to override this one as well.
	 * @param timeout the timeout
	 * @param includeDetails if details should be included or removed
	 * @return a {@link Mono} that provides the {@link Health}, see
	 * {@link #health(Duration)}
	 * @since 4.2.0
	 */
	default Mono<Health> health(Duration timeout, boolean includeDetails) {
		Mono<Health> health = health(timeout);
		return includeDetails ? health : health.map(Health::withoutDetails);
	}

}
