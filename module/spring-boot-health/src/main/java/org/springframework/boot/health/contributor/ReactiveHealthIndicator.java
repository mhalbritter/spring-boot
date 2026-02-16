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

import reactor.core.publisher.Mono;

/**
 * Directly contributes {@link Health} information for specific reactive component or
 * subsystem.
 * <p>
 * This is non-blocking contract that is meant to be used in a reactive application. See
 * {@link HealthIndicator} for the traditional contract.
 *
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @since 4.0.0
 * @see HealthIndicator
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
	 * Returns how this indicator participates when a timeout is configured (see
	 * {@link ReactiveHealthIndicatorExecutor}).
	 * <ul>
	 * <li>{@link TimeoutSupport#NONE} &mdash; the configured timeout is not enforced; a
	 * warning is logged once per indicator name if a timeout is configured anyway.</li>
	 * <li>{@link TimeoutSupport#NATIVE} &mdash; {@link #health(Duration)} (or
	 * {@link #health(Duration, boolean)}) is called so the indicator can apply the limit
	 * with reactive or blocking APIs inside the returned {@link Mono}.</li>
	 * <li>{@link TimeoutSupport#INTERRUPTION} &mdash; {@link Mono#timeout(Duration)} is
	 * applied to {@link #health()} (or {@link #health(boolean)}), which cancels the
	 * subscription on timeout.</li>
	 * </ul>
	 * @return the timeout support of that indicator
	 * @since 4.1.0
	 */
	default TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NONE;
	}

	/**
	 * Provide the indicator of health, respecting the given timeout. This method will
	 * only be called if {@link #getTimeoutSupport()} returns
	 * {@link TimeoutSupport#NATIVE}.
	 * @param timeout the timeout
	 * @return a {@link Mono} that provides the {@link Health}
	 * @since 4.1.0
	 */
	default Mono<Health> health(Duration timeout) {
		throw new UnsupportedOperationException("Timeout is not supported");
	}

	/**
	 * Provide the indicator of health, respecting the given timeout. This method will
	 * only be called if {@link #getTimeoutSupport()} returns
	 * {@link TimeoutSupport#NATIVE}.
	 * @param includeDetails if details should be included or removed
	 * @param timeout the timeout
	 * @return a {@link Mono} that provides the {@link Health}
	 * @since 4.1.0
	 */
	default Mono<Health> health(Duration timeout, boolean includeDetails) {
		Mono<Health> health = health(timeout);
		return includeDetails ? health : health.map(Health::withoutDetails);
	}

}
