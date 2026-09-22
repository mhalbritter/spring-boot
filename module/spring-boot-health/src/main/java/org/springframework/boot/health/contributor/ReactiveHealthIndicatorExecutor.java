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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.context.Context;

import org.springframework.boot.health.contributor.HealthIndicatorTimeouts.InvalidTimeoutException;
import org.springframework.boot.health.contributor.InFlightExecutions.Check;
import org.springframework.boot.health.contributor.InFlightExecutions.Execution;
import org.springframework.boot.health.contributor.InFlightExecutions.Key;
import org.springframework.boot.health.contributor.InFlightExecutions.TooManyChecksInFlightException;
import org.springframework.util.Assert;

/**
 * Allows to execute {@link ReactiveHealthIndicator ReactiveHealthIndicators} with a
 * timeout.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public class ReactiveHealthIndicatorExecutor {

	private static final Log logger = LogFactory.getLog(ReactiveHealthIndicatorExecutor.class);

	private final InFlightExecutions<SharedCheck> inFlight = new InFlightExecutions<>();

	private final HealthIndicatorTimeouts timeouts;

	private final TimeoutEnforcementResolver timeoutEnforcements = new TimeoutEnforcementResolver();

	private final HealthIndicatorExecutor blockingExecutor;

	/**
	 * Creates a new instance.
	 * @param blockingExecutor the executor to run adapted blocking indicators on
	 */
	public ReactiveHealthIndicatorExecutor(HealthIndicatorExecutor blockingExecutor) {
		Assert.notNull(blockingExecutor, "'blockingExecutor' must not be null");
		this.blockingExecutor = blockingExecutor;
		this.timeouts = blockingExecutor.getTimeouts();
	}

	/**
	 * Executes a {@link ReactiveHealthIndicator} with a timeout if necessary. Never
	 * throws and never signals an error: a timeout, an invalid timeout configuration or
	 * any other failure of the indicator is turned into {@link Health#down()} with a
	 * {@code reason} detail.
	 * @param reactiveHealthIndicator the indicator to execute
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return the health
	 */
	public Mono<Health> execute(ReactiveHealthIndicator reactiveHealthIndicator, String indicatorName,
			boolean includeDetails) {
		Mono<Health> health = Mono.defer(() -> doExecute(reactiveHealthIndicator, indicatorName, includeDetails));
		return safeguard(health, indicatorName, includeDetails);
	}

	private Mono<Health> doExecute(ReactiveHealthIndicator reactiveHealthIndicator, String indicatorName,
			boolean includeDetails) {
		if (reactiveHealthIndicator instanceof HealthIndicatorAdapter adapted) {
			return executeBlocking(adapted, indicatorName, includeDetails);
		}
		Duration timeout = this.timeouts.get(indicatorName);
		if (timeout == null) {
			return reactiveHealthIndicator.health(includeDetails);
		}
		TimeoutEnforcement enforcement = this.timeoutEnforcements
			.resolve(reactiveHealthIndicator.getTimeoutEnforcement(), reactiveHealthIndicator);
		Supplier<Mono<Health>> check = this.timeoutEnforcements.acceptsTimeout(reactiveHealthIndicator)
				? () -> reactiveHealthIndicator.health(timeout, includeDetails)
				: () -> reactiveHealthIndicator.health(includeDetails);
		return switch (enforcement) {
			case INDICATOR -> check.get();
			case FRAMEWORK -> joinOrStart(check, indicatorName, timeout, includeDetails);
		};
	}

	/**
	 * Shares a single check between all callers of the same indicator.
	 * @param check the check to run
	 * @param indicatorName the name of the indicator
	 * @param timeout the timeout
	 * @param includeDetails whether to include details
	 * @return the shared health, or a {@code concurrency-limit} {@link Health#down()} if
	 * the indicator has too many checks in flight
	 */
	private Mono<Health> joinOrStart(Supplier<Mono<Health>> check, String indicatorName, Duration timeout,
			boolean includeDetails) {
		Key key = new Key(indicatorName, includeDetails);
		return Mono.defer(() -> {
			Execution<SharedCheck> execution;
			try {
				Runnable onEnd = () -> this.inFlight.finished(key);
				execution = this.inFlight.joinOrStart(key, timeout, () -> new SharedCheck(check, timeout, onEnd));
			}
			catch (TooManyChecksInFlightException ex) {
				return Mono.just(DownHealth.of(ex, DownReason.CONCURRENCY_LIMIT, includeDetails));
			}
			return execution.check().health();
		});
	}

	/**
	 * Runs an adapted blocking indicator on the blocking executor, timeout included.
	 * @param adapted the adapted indicator
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return the health
	 */
	private Mono<Health> executeBlocking(HealthIndicatorAdapter adapted, String indicatorName, boolean includeDetails) {
		return Mono.fromFuture(() -> this.blockingExecutor.execute(adapted.getDelegate(), indicatorName, includeDetails,
				ThreadingMode.POOL));
	}

	private Mono<Health> safeguard(Mono<Health> health, String indicatorName, boolean includeDetails) {
		return health.onErrorResume((ex) -> Mono.just(toDownHealth(ex, indicatorName, includeDetails)));
	}

	private Health toDownHealth(Throwable ex, String indicatorName, boolean includeDetails) {
		TimeoutException timeout = HealthIndicatorExecutor.asTimeout(ex);
		if (timeout != null) {
			return DownHealth.of(timeout, DownReason.TIMEOUT, includeDetails);
		}
		if (ex instanceof InvalidTimeoutException) {
			return DownHealth.logged(logger, ex, DownReason.INVALID_TIMEOUT, indicatorName, includeDetails);
		}
		return DownHealth.logged(logger, ex, DownReason.EXECUTION_FAILED, indicatorName, includeDetails);
	}

	/**
	 * A check which is subscribed to once, when it is started, and whose result is
	 * replayed to every caller joined to it.
	 * <p>
	 * The check runs with an empty {@link Context}.
	 */
	private static final class SharedCheck implements Check {

		private final Sinks.One<Health> result = Sinks.one();

		private final AtomicBoolean ended = new AtomicBoolean();

		private final Supplier<Mono<Health>> source;

		private final Duration timeout;

		private final Runnable onEnd;

		private SharedCheck(Supplier<Mono<Health>> source, Duration timeout, Runnable onEnd) {
			this.source = source;
			this.timeout = timeout;
			this.onEnd = onEnd;
		}

		@Override
		public void start() {
			this.source.get().timeout(this.timeout).doFinally((signal) -> {
				this.ended.set(true);
				this.onEnd.run();
			}).subscribe(this.result::tryEmitValue, this.result::tryEmitError, this.result::tryEmitEmpty);
		}

		private Mono<Health> health() {
			return this.result.asMono();
		}

		@Override
		public boolean hasEnded() {
			return this.ended.get();
		}

	}

}
