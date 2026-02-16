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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.thread.Threading;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * Allows to execute {@link HealthIndicator HealthIndicators} with a timeout.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
public class HealthIndicatorExecutor extends AbstractHealthIndicatorExecutor implements DisposableBean {

	private static final Log logger = LogFactory.getLog(HealthIndicatorExecutor.class);

	private static final int MAX_PLATFORM_THREADS = 20;

	private static final int PLATFORM_THREAD_QUEUE_SIZE = 1000;

	private static final Duration THREAD_KEEP_ALIVE = Duration.ofMinutes(1);

	private final Object executorServiceLock = new Object();

	private final @Nullable Environment environment;

	private boolean disposed;

	private @Nullable ExecutorService executorService;

	public HealthIndicatorExecutor(@Nullable Environment environment) {
		super(logger, environment);
		this.environment = environment;
	}

	/**
	 * Executes a {@link HealthIndicator}, with a timeout if necessary. If a timeout
	 * occurs, {@link Health#down()} with reason 'timeout' is returned.
	 * @param indicator the indicator to execute
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return a future that completes with the health
	 */
	public CompletableFuture<@Nullable Health> execute(HealthIndicator indicator, String indicatorName,
			boolean includeDetails) {
		Duration timeout = getTimeout(indicatorName);
		if (timeout == null) {
			return CompletableFuture.completedFuture(indicator.health(includeDetails));
		}
		try {
			Health value = switch (indicator.getTimeoutSupport()) {
				case NONE -> {
					logTimeoutNotSupportedWarningOnce(indicatorName, timeout);
					yield indicator.health(includeDetails);
				}
				case NATIVE -> indicator.health(timeout, includeDetails);
				case INTERRUPTION -> executeOnExecutorService(getExecutorService(), indicator, timeout, includeDetails);
			};
			return CompletableFuture.completedFuture(value);
		}
		catch (TimeoutException ex) {
			return CompletableFuture.completedFuture(Health.down(ex).withDetail("reason", "timeout").build());
		}
	}

	private ExecutorService getExecutorService() {
		synchronized (this.executorServiceLock) {
			Assert.state(!this.disposed, "Executor service is already disposed");
			if (this.executorService == null) {
				this.executorService = createExecutor();
			}
			return this.executorService;
		}
	}

	private ExecutorService createExecutor() {
		Assert.state(this.environment != null, "'this.environment' must not be null");
		if (Threading.VIRTUAL.isActive(this.environment)) {
			return createExecutorForVirtualThreading();
		}
		return createExecutorForPlatformThreading();
	}

	private ExecutorService createExecutorForPlatformThreading() {
		AtomicLong counter = new AtomicLong();
		ThreadFactory threadFactory = (runnable) -> {
			Thread thread = new Thread(runnable, "health-timeout-" + counter.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		};
		ThreadPoolExecutor executor = new ThreadPoolExecutor(MAX_PLATFORM_THREADS, MAX_PLATFORM_THREADS,
				THREAD_KEEP_ALIVE.getSeconds(), TimeUnit.SECONDS, new LinkedBlockingQueue<>(PLATFORM_THREAD_QUEUE_SIZE),
				threadFactory);
		executor.allowCoreThreadTimeOut(true);
		executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
			logger.warn("Unable to execute health check with timeout");
			throw new RejectedExecutionException("Unable to execute health check with timeout");
		});
		return executor;
	}

	private ExecutorService createExecutorForVirtualThreading() {
		Method method = ReflectionUtils.findMethod(Executors.class, "newVirtualThreadPerTaskExecutor");
		Assert.state(method != null, "Unable to find Executors.newVirtualThreadPerTaskExecutor()");
		ExecutorService executorService = (ExecutorService) ReflectionUtils.invokeMethod(method, null);
		Assert.state(executorService != null, "'executorService' must not be null");
		return executorService;
	}

	private @Nullable Health executeOnExecutorService(ExecutorService executorService, HealthIndicator indicator,
			Duration timeout, boolean includeDetails) throws TimeoutException {
		Future<@Nullable Health> future = executorService.submit(() -> indicator.health(includeDetails));
		try {
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			throw ex;
		}
		catch (ExecutionException ex) {
			future.cancel(true);
			throw new RuntimeException("Exception while running the health indicator", ex.getCause());
		}
		catch (InterruptedException ex) {
			future.cancel(true);
			Thread.currentThread().interrupt();
			throw new RuntimeException("Got interrupted while waiting for the health indicator", ex);
		}
	}

	@Override
	public void destroy() throws Exception {
		synchronized (this.executorServiceLock) {
			this.disposed = true;
			if (this.executorService != null) {
				this.executorService.shutdown();
				if (!this.executorService.awaitTermination(5, TimeUnit.SECONDS)) {
					this.executorService.shutdownNow();
				}
			}
		}
	}

}
