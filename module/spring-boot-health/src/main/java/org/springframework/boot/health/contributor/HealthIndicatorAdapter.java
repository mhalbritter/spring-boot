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
import reactor.core.scheduler.Schedulers;

import org.springframework.util.Assert;

/**
 * Adapts a {@link HealthIndicator} to a {@link ReactiveHealthIndicator} so that it can be
 * safely invoked in a reactive environment.
 *
 * @author Stephane Nicoll
 * @see ReactiveHealthContributor#adapt(HealthContributor)
 */
class HealthIndicatorAdapter implements ReactiveHealthIndicator {

	private final HealthIndicator delegate;

	HealthIndicatorAdapter(HealthIndicator delegate) {
		Assert.notNull(delegate, "'delegate' must not be null");
		this.delegate = delegate;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return this.delegate.getTimeoutSupport();
	}

	@Override
	public Mono<Health> health() {
		return Mono.fromCallable(this.delegate::health).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<Health> health(boolean includeDetails) {
		return Mono.fromCallable(() -> this.delegate.health(includeDetails)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<Health> health(Duration timeout) {
		return Mono.fromCallable(() -> this.delegate.health(timeout)).subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public Mono<Health> health(Duration timeout, boolean includeDetails) {
		return Mono.fromCallable(() -> this.delegate.health(timeout, includeDetails))
			.subscribeOn(Schedulers.boundedElastic());
	}

}
