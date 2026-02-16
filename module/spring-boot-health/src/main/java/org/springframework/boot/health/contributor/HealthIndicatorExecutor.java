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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.health.contributor.HealthIndicatorTimeouts.InvalidTimeoutException;
import org.springframework.boot.health.contributor.InFlightExecutions.Check;
import org.springframework.boot.health.contributor.InFlightExecutions.Execution;
import org.springframework.boot.health.contributor.InFlightExecutions.Key;
import org.springframework.boot.health.contributor.InFlightExecutions.TooManyChecksInFlightException;
import org.springframework.boot.thread.Threading;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Allows to execute {@link HealthIndicator HealthIndicators} with a timeout.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public class HealthIndicatorExecutor implements DisposableBean {

	private static final Log logger = LogFactory.getLog(HealthIndicatorExecutor.class);

	private static final Duration THREAD_KEEP_ALIVE = Duration.ofMinutes(1);

	private static final long NANOS_PER_MILLI = 1_000_000;

	private static final String THREAD_NAME_PREFIX = "health-check-";

	private final Object executorServiceLock = new Object();

	private final InFlightExecutions<AsyncCheck> inFlight = new InFlightExecutions<>();

	private final HealthIndicatorTimeouts timeouts;

	private final PropertyResolver propertyResolver;

	private boolean disposed;

	private @Nullable ExecutorService executorService;

	/**
	 * Creates a new instance.
	 * @param propertyResolver the property resolver to read the timeouts and the
	 * threading from
	 */
	public HealthIndicatorExecutor(PropertyResolver propertyResolver) {
		Assert.notNull(propertyResolver, "'propertyResolver' must not be null");
		this.timeouts = new HealthIndicatorTimeouts(propertyResolver);
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Returns the timeouts this executor applies, so that a
	 * {@link ReactiveHealthIndicatorExecutor} in front of it resolves a timeout the same
	 * way and from the same cache.
	 * @return the timeouts
	 */
	HealthIndicatorTimeouts getTimeouts() {
		return this.timeouts;
	}

	/**
	 * Executes a {@link HealthIndicator}, with a timeout if necessary. Never throws: a
	 * timeout, a failing indicator, an invalid timeout configuration, a saturated
	 * executor or a call after {@link #destroy()} completes the future with
	 * {@link Health#down()} and a {@code reason} detail instead.
	 * <p>
	 * The {@code reason} and the exception are details, so a caller which does not ask
	 * for details is told no more than {@link Health#down()}.
	 * <p>
	 * Only a {@link TimeoutEnforcement#FRAMEWORK} check runs on another thread, and
	 * nothing waits for it. Every other check runs on the calling thread and the returned
	 * future is already completed.
	 * @param indicator the indicator to execute
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return a future that completes with the health
	 */
	public CompletableFuture<@Nullable Health> execute(HealthIndicator indicator, String indicatorName,
			boolean includeDetails) {
		return execute(indicator, indicatorName, includeDetails, ThreadingMode.CALLING_THREAD);
	}

	/**
	 * Executes a {@link HealthIndicator} as
	 * {@link #execute(HealthIndicator, String, boolean)} does, but lets the caller decide
	 * where a check without a {@link TimeoutEnforcement#FRAMEWORK} deadline runs.
	 * @param indicator the indicator to execute
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @param threadingMode where a check without a framework deadline runs
	 * @return a future that completes with the health
	 */
	CompletableFuture<@Nullable Health> execute(HealthIndicator indicator, String indicatorName, boolean includeDetails,
			ThreadingMode threadingMode) {
		Duration timeout;
		try {
			timeout = this.timeouts.get(indicatorName);
		}
		catch (InvalidTimeoutException ex) {
			return CompletableFuture.completedFuture(
					DownHealth.logged(logger, ex, DownReason.INVALID_TIMEOUT, indicatorName, includeDetails));
		}
		if (timeout == null) {
			return executeWithoutDeadline(indicatorName, includeDetails, threadingMode,
					() -> indicator.health(includeDetails));
		}
		return switch (indicator.getTimeoutEnforcement()) {
			case INDICATOR -> executeWithoutDeadline(indicatorName, includeDetails, threadingMode,
					() -> indicator.health(timeout, includeDetails));
			case FRAMEWORK -> executeOnExecutorService(indicator, indicatorName, timeout, includeDetails);
		};
	}

	/**
	 * Runs a check which this executor puts no deadline on, either because none is
	 * configured or because the indicator enforces it itself.
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @param threadingMode where the check runs
	 * @param check the check to run
	 * @return a future that completes with the health
	 */
	// On the pool such a check stays joinable until it ends, so one which never returns
	// occupies a single thread instead of one per caller.
	private CompletableFuture<@Nullable Health> executeWithoutDeadline(String indicatorName, boolean includeDetails,
			ThreadingMode threadingMode, Callable<@Nullable Health> check) {
		return switch (threadingMode) {
			case CALLING_THREAD -> executeOnCallingThread(check, indicatorName, includeDetails);
			case POOL -> submit(new Key(indicatorName, includeDetails), null, check);
		};
	}

	private CompletableFuture<@Nullable Health> executeOnCallingThread(Callable<@Nullable Health> check,
			String indicatorName, boolean includeDetails) {
		try {
			return CompletableFuture.completedFuture(check.call());
		}
		catch (Exception ex) {
			TimeoutException timeout = asTimeout(ex);
			if (timeout != null) {
				return CompletableFuture.completedFuture(DownHealth.of(timeout, DownReason.TIMEOUT, includeDetails));
			}
			return CompletableFuture.completedFuture(
					DownHealth.logged(logger, ex, DownReason.EXECUTION_FAILED, indicatorName, includeDetails));
		}
	}

	/**
	 * Returns the executor service to run checks on, creating it if necessary.
	 * @return the executor service
	 * @throws ExecutorDisposedException if this executor has been destroyed
	 */
	private ExecutorService getExecutorService() throws ExecutorDisposedException {
		synchronized (this.executorServiceLock) {
			if (this.disposed) {
				throw new ExecutorDisposedException();
			}
			if (this.executorService == null) {
				this.executorService = createExecutor();
			}
			return this.executorService;
		}
	}

	ExecutorService createExecutor() {
		if (Threading.VIRTUAL.isActive(this.propertyResolver)) {
			return createExecutorForVirtualThreading();
		}
		return createExecutorForPlatformThreading();
	}

	private ExecutorService createExecutorForVirtualThreading() {
		Method method = ReflectionUtils.findMethod(Executors.class, "newVirtualThreadPerTaskExecutor");
		Assert.state(method != null, "Unable to find Executors.newVirtualThreadPerTaskExecutor()");
		ExecutorService executorService = (ExecutorService) ReflectionUtils.invokeMethod(method, null);
		Assert.state(executorService != null, "'executorService' must not be null");
		return executorService;
	}

	private ExecutorService createExecutorForPlatformThreading() {
		AtomicLong counter = new AtomicLong();
		ThreadFactory threadFactory = (runnable) -> {
			Thread thread = new Thread(runnable, THREAD_NAME_PREFIX + counter.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		};
		// No queue: the timeout budget of a check starts when it is submitted, so a
		// queued check would spend it waiting and report DOWN without ever running. The
		// thread count is instead bounded by the per-indicator execution limit, giving at
		// most four threads per indicator, so the pool itself needs no maximum.
		return new ThreadPoolExecutor(0, Integer.MAX_VALUE, THREAD_KEEP_ALIVE.getSeconds(), TimeUnit.SECONDS,
				new SynchronousQueue<>(), threadFactory);
	}

	private CompletableFuture<@Nullable Health> executeOnExecutorService(HealthIndicator indicator,
			String indicatorName, Duration timeout, boolean includeDetails) {
		return submit(new Key(indicatorName, includeDetails), timeout, () -> indicator.health(includeDetails));
	}

	private CompletableFuture<@Nullable Health> submit(Key key, @Nullable Duration deadline,
			Callable<@Nullable Health> check) {
		ExecutorService executorService;
		try {
			executorService = getExecutorService();
		}
		catch (ExecutorDisposedException ex) {
			return CompletableFuture.completedFuture(DownHealth.of(ex, DownReason.DISPOSED, key.includeDetails()));
		}
		Execution<AsyncCheck> execution;
		try {
			execution = this.inFlight.join(key, deadline, () -> start(executorService, key, deadline, check));
		}
		catch (TooManyChecksInFlightException ex) {
			return CompletableFuture
				.completedFuture(DownHealth.of(ex, DownReason.CONCURRENCY_LIMIT, key.includeDetails()));
		}
		catch (RejectedExecutionException ex) {
			return CompletableFuture.completedFuture(
					DownHealth.logged(logger, ex, DownReason.REJECTED, key.indicatorName(), key.includeDetails()));
		}
		return execution.check().result().<@Nullable Health>handle((health, ex) -> toHealth(health, ex, key));
	}

	private @Nullable Health toHealth(@Nullable Health health, @Nullable Throwable ex, Key key) {
		if (ex == null) {
			return health;
		}
		Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
		TimeoutException timeout = asTimeout(cause);
		if (timeout != null) {
			return DownHealth.of(timeout, DownReason.TIMEOUT, key.includeDetails());
		}
		return DownHealth.logged(logger, cause, DownReason.EXECUTION_FAILED, key.indicatorName(), key.includeDetails());
	}

	/**
	 * Returns the {@link TimeoutException} a failure stands for, if it is one.
	 * @param ex the failure
	 * @return the timeout, or {@code null} if the failure is not one
	 */
	// A checked TimeoutException cannot cross an API which does not declare it, so an
	// adapted reactive indicator reports it wrapped in the RuntimeException
	// Mono#block() throws. Without unwrapping, the same indicator answers
	// reason: "timeout" in a reactive application and reason: "execution-failed" in a
	// servlet one.
	static @Nullable TimeoutException asTimeout(Throwable ex) {
		if (ex instanceof TimeoutException timeout) {
			return timeout;
		}
		return (ex.getCause() instanceof TimeoutException timeout) ? timeout : null;
	}

	private AsyncCheck start(ExecutorService executorService, Key key, @Nullable Duration deadline,
			Callable<@Nullable Health> check) {
		// The task ends its own execution: a timeout completes the callers while the task
		// can still hold the thread we are capping, and a cancelled task which ignores
		// interruption never stops holding it.
		return new AsyncCheck(executorService, check, () -> this.inFlight.finished(key), deadline);
	}

	@Override
	public void destroy() {
		ExecutorService executorService;
		synchronized (this.executorServiceLock) {
			this.disposed = true;
			executorService = this.executorService;
			this.executorService = null;
		}
		if (executorService != null) {
			executorService.shutdownNow();
		}
	}

	/**
	 * Thrown when a check is executed after {@link #destroy()} has been called.
	 */
	private static final class ExecutorDisposedException extends IllegalStateException {

		private ExecutorDisposedException() {
			super("Executor service is already disposed");
		}

	}

	/**
	 * A check which runs on the executor service and completes a result which every
	 * caller joined to it observes. Nothing waits for that result: the worker which runs
	 * the check completes it.
	 */
	private static final class AsyncCheck implements Check {

		private final CompletableFuture<@Nullable Health> result = new CompletableFuture<>();

		private final AtomicBoolean running = new AtomicBoolean();

		private final ExecutorService executorService;

		private final Callable<@Nullable Health> check;

		private final Runnable onEnd;

		private final @Nullable Duration timeout;

		private volatile @Nullable Future<?> task;

		private AsyncCheck(ExecutorService executorService, Callable<@Nullable Health> check, Runnable onEnd,
				@Nullable Duration timeout) {
			this.executorService = executorService;
			this.check = check;
			this.onEnd = onEnd;
			this.timeout = timeout;
		}

		@Override
		public void start() {
			this.task = this.executorService.submit(() -> run(this.check));
			if (this.timeout == null) {
				return;
			}
			// One timer for the whole execution, rounded up, so that the
			// result never completes before the execution turns stale, which would leave
			// the next caller joining a check nobody is going to restart.
			this.result.orTimeout(toMillisRoundedUp(this.timeout), TimeUnit.MILLISECONDS)
				.whenComplete((health, ex) -> cancelOnTimeout(ex));
		}

		private void cancelOnTimeout(@Nullable Throwable ex) {
			if (!(ex instanceof TimeoutException)) {
				return;
			}
			Future<?> task = this.task;
			if (task != null) {
				task.cancel(true);
			}
			if (this.running.compareAndSet(false, true)) {
				this.onEnd.run();
			}
		}

		private static long toMillisRoundedUp(Duration duration) {
			return duration.plusNanos(NANOS_PER_MILLI - 1).toMillis();
		}

		private void run(Callable<@Nullable Health> check) {
			if (!this.running.compareAndSet(false, true)) {
				return;
			}
			Health health = null;
			Throwable error = null;
			try {
				health = check.call();
			}
			catch (Throwable ex) {
				error = ex;
			}
			this.onEnd.run();
			if (error != null) {
				this.result.completeExceptionally(error);
				return;
			}
			this.result.complete(health);
		}

		private CompletableFuture<@Nullable Health> result() {
			return this.result;
		}

		@Override
		public boolean hasEnded() {
			return this.result.isDone();
		}

	}

}
