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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import reactor.core.Exceptions;

import org.springframework.util.Assert;

/**
 * Adapts a {@link ReactiveHealthIndicator} to a {@link HealthIndicator}, keeping its
 * {@link TimeoutEnforcement}.
 *
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @see ReactiveHealthIndicator#asHealthContributor()
 */
class ReactiveHealthIndicatorAdapter implements HealthIndicator {

	private final ReactiveHealthIndicator delegate;

	ReactiveHealthIndicatorAdapter(ReactiveHealthIndicator delegate) {
		Assert.notNull(delegate, "'delegate' must not be null");
		this.delegate = delegate;
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
	public TimeoutEnforcement getTimeoutEnforcement() {
		return this.delegate.getTimeoutEnforcement();
	}

	@Override
	public @Nullable Health health(Duration timeout) throws TimeoutException {
		return unwrapTimeout(() -> this.delegate.health(timeout).block());
	}

	@Override
	public @Nullable Health health(Duration timeout, boolean includeDetails) throws TimeoutException {
		return unwrapTimeout(() -> this.delegate.health(timeout, includeDetails).block());
	}

	private @Nullable Health unwrapTimeout(Supplier<@Nullable Health> blockingCall) throws TimeoutException {
		try {
			return blockingCall.get();
		}
		catch (RuntimeException ex) {
			if (findTimeout(ex) instanceof TimeoutException timeoutException) {
				throw timeoutException;
			}
			throw ex;
		}
	}

	/**
	 * Strips the wrappers a blocking call can add around the original failure, for
	 * example Reactor's own exceptions or a {@link CompletionException} raised by a
	 * delegate that bridges a {@link java.util.concurrent.CompletableFuture}.
	 * @param ex the exception thrown by the blocking call
	 * @return the unwrapped exception
	 */
	private Throwable findTimeout(Throwable ex) {
		Throwable candidate = Exceptions.unwrap(ex);
		while ((candidate instanceof CompletionException || candidate instanceof ExecutionException)
				&& candidate.getCause() != null) {
			candidate = Exceptions.unwrap(candidate.getCause());
		}
		return candidate;
	}

}
