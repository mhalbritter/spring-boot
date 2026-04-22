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

package org.springframework.boot.jms.health;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.TimeoutSupport;

/**
 * {@link HealthIndicator} for a JMS {@link ConnectionFactory}.
 * <p>
 * This indicator uses {@link TimeoutSupport#NATIVE}: when a health timeout is configured,
 * the {@link MonitoredConnection} can bound how long {@link Connection#start()} may
 * block. A companion thread waits for the configured duration; if
 * {@link Connection#start()} has not finished, the connection is
 * {@linkplain Connection#close() closed} to unblock the client. When no health timeout
 * applies, {@link #doHealthCheck(Health.Builder)} still uses a fixed watchdog for hung
 * {@link Connection#start()} calls.
 *
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class JmsHealthIndicator extends AbstractHealthIndicator {

	private static final Duration DEFAULT_CONNECTION_START_WATCHDOG = Duration.ofSeconds(5);

	private final Log logger = LogFactory.getLog(JmsHealthIndicator.class);

	private final ConnectionFactory connectionFactory;

	public JmsHealthIndicator(ConnectionFactory connectionFactory) {
		super("JMS health check failed");
		this.connectionFactory = connectionFactory;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		performHealthCheck(builder, DEFAULT_CONNECTION_START_WATCHDOG, false);
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		performHealthCheck(builder, effectiveNativeWatchdog(timeout), true);
	}

	private void performHealthCheck(Health.Builder builder, Duration watchdog, boolean reportWatchdogAsTimeout)
			throws Exception {
		try (Connection connection = this.connectionFactory.createConnection()) {
			new MonitoredConnection(connection).start(watchdog, reportWatchdogAsTimeout);
			builder.up().withDetail("provider", connection.getMetaData().getJMSProviderName());
		}
	}

	private Duration effectiveNativeWatchdog(Duration timeout) {
		if (timeout.isNegative() || timeout.isZero()) {
			return Duration.ofMillis(1);
		}
		return (timeout.toMillis() < 1) ? Duration.ofMillis(1) : timeout;
	}

	private final class MonitoredConnection {

		private final CountDownLatch latch = new CountDownLatch(1);

		private final Connection connection;

		private volatile boolean watchdogTriggered;

		MonitoredConnection(Connection connection) {
			this.connection = connection;
		}

		void start(Duration watchdog, boolean reportWatchdogAsTimeout) throws Exception {
			long awaitMillis = Math.max(1, watchdog.toMillis());
			new Thread(() -> {
				try {
					if (!this.latch.await(awaitMillis, TimeUnit.MILLISECONDS)) {
						this.watchdogTriggered = true;
						JmsHealthIndicator.this.logger
							.warn("Connection failed to start within %s and will be closed.".formatted(watchdog));
						closeConnection();
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}, "jms-health-indicator").start();
			try {
				this.connection.start();
			}
			catch (JMSException ex) {
				if (this.watchdogTriggered && reportWatchdogAsTimeout) {
					TimeoutException timeoutException = new TimeoutException(
							"JMS connection failed to start within " + watchdog);
					timeoutException.initCause(ex);
					throw timeoutException;
				}
				throw ex;
			}
			finally {
				this.latch.countDown();
			}
		}

		private void closeConnection() {
			try {
				this.connection.close();
			}
			catch (Exception ex) {
				// Continue
			}
		}

	}

}
