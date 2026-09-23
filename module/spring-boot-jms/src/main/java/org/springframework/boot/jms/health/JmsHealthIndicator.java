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

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.core.log.LogMessage;

/**
 * {@link HealthIndicator} for a JMS {@link ConnectionFactory}.
 *
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class JmsHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

	private static final Log logger = LogFactory.getLog(JmsHealthIndicator.class);

	private final ConnectionFactory connectionFactory;

	/**
	 * Create a new {@link JmsHealthIndicator} instance.
	 * @param connectionFactory the connection factory to check
	 */
	public JmsHealthIndicator(ConnectionFactory connectionFactory) {
		super(TimeoutEnforcement.FRAMEWORK, "JMS health check failed");
		this.connectionFactory = connectionFactory;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception {
		Duration effectiveTimeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
		try (Connection connection = this.connectionFactory.createConnection()) {
			String provider = new MonitoredConnection(connection).startAndGetProvider(effectiveTimeout);
			builder.up().withDetail("provider", provider);
		}
	}

	private static final class MonitoredConnection {

		private final CountDownLatch latch = new CountDownLatch(1);

		private final Connection connection;

		MonitoredConnection(Connection connection) {
			this.connection = connection;
		}

		String startAndGetProvider(Duration timeout) throws JMSException {
			Thread watchdogThread = new Thread(() -> {
				try {
					if (!this.latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
						logger.warn(LogMessage.format(
								"Connection failed to start or provide its metadata within %s and will be closed.",
								DurationStyle.SIMPLE.print(timeout)));
						closeConnection();
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}, "jms-health-indicator");
			watchdogThread.setDaemon(true);
			watchdogThread.start();
			try {
				this.connection.start();
				return this.connection.getMetaData().getJMSProviderName();
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
