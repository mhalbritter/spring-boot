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
 * Adapts a {@link ReactiveHealthIndicator} to a {@link HealthIndicator}.
 *
 * @author Phillip Webb
 * @see ReactiveHealthIndicator#asHealthContributor()
 */
class ReactiveHealthIndicatorAdapter implements HealthIndicator {

	private final ReactiveHealthIndicator delegate;

	ReactiveHealthIndicatorAdapter(ReactiveHealthIndicator indicator) {
		this.delegate = indicator;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return this.delegate.getTimeoutSupport();
	}

	@Override
	public @Nullable Health health(boolean includeDetails) {
		return this.delegate.health(includeDetails).block();
	}

	@Override
	public @Nullable Health health() {
		return this.delegate.health().block();
	}

	@Override
	public @Nullable Health health(Duration timeout) throws TimeoutException {
		try {
			return this.delegate.health(timeout).block();
		}
		catch (RuntimeException ex) {
			if (ex.getCause() instanceof TimeoutException timeoutException) {
				throw timeoutException;
			}
			throw ex;
		}
	}

	@Override
	public @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
		try {
			return this.delegate.health(timeout, includeDetails).block();
		}
		catch (RuntimeException ex) {
			if (ex.getCause() instanceof TimeoutException timeoutException) {
				throw timeoutException;
			}
			throw ex;
		}
	}

}
