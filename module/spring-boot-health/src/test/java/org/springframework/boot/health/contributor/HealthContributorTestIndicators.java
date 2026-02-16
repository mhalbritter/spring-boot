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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import reactor.core.publisher.Mono;

/**
 * Factory methods for health indicators used in executor tests.
 *
 * @author Moritz Halbritter
 */
final class HealthContributorTestIndicators {

	private HealthContributorTestIndicators() {
	}

	static HealthIndicator interruptionHealthIndicator(Supplier<Health> healthSupplier) {
		return new HealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.INTERRUPTION;
			}

			@Override
			public Health health() {
				return healthSupplier.get();
			}
		};
	}

	static HealthIndicator blockingSleepInterruption(Duration sleep) {
		return interruptionHealthIndicator(() -> {
			try {
				Thread.sleep(sleep.toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(ex);
			}
			return Health.up().build();
		});
	}

	static HealthIndicator blockingSleepInterruption(Duration sleep, AtomicBoolean interruptedFlag) {
		return interruptionHealthIndicator(() -> {
			try {
				Thread.sleep(sleep.toMillis());
			}
			catch (InterruptedException ex) {
				interruptedFlag.set(true);
				Thread.currentThread().interrupt();
				throw new RuntimeException(ex);
			}
			return Health.up().build();
		});
	}

	static ReactiveHealthIndicator interruptionReactiveHealthIndicator(Supplier<Mono<Health>> healthSupplier) {
		return new ReactiveHealthIndicator() {
			@Override
			public TimeoutSupport getTimeoutSupport() {
				return TimeoutSupport.INTERRUPTION;
			}

			@Override
			public Mono<Health> health() {
				return healthSupplier.get();
			}
		};
	}

	static ReactiveHealthIndicator delayedInterruption(Duration delay) {
		return interruptionReactiveHealthIndicator(() -> Mono.just(Health.up().build()).delayElement(delay));
	}

}
