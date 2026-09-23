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

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicatorExecutor;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;

/**
 * Tests for {@link JmsHealthIndicator}.
 *
 * @author Stephane Nicoll
 */
class JmsHealthIndicatorTests {

	@Test
	void getTimeoutEnforcementIsFramework() {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		assertThat(indicator.getTimeoutEnforcement()).isEqualTo(TimeoutEnforcement.FRAMEWORK);
	}

	@Test
	void jmsBrokerIsUp() throws JMSException {
		ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
		given(connectionMetaData.getJMSProviderName()).willReturn("JMS test provider");
		Connection connection = mock(Connection.class);
		given(connection.getMetaData()).willReturn(connectionMetaData);
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		given(connectionFactory.createConnection()).willReturn(connection);
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		Health health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("provider", "JMS test provider");
		then(connection).should().close();
	}

	@Test
	void jmsBrokerIsDown() throws JMSException {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		given(connectionFactory.createConnection()).willThrow(new JMSException("test", "123"));
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		Health health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).doesNotContainKey("provider");
	}

	@Test
	void jmsBrokerCouldNotRetrieveProviderMetadata() throws JMSException {
		ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
		given(connectionMetaData.getJMSProviderName()).willThrow(new JMSException("test", "123"));
		Connection connection = mock(Connection.class);
		given(connection.getMetaData()).willReturn(connectionMetaData);
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		given(connectionFactory.createConnection()).willReturn(connection);
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		Health health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).doesNotContainKey("provider");
		then(connection).should().close();
	}

	@Test
	void jmsBrokerUsesFailover() throws JMSException {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
		given(connectionMetaData.getJMSProviderName()).willReturn("JMS test provider");
		Connection connection = mock(Connection.class);
		given(connection.getMetaData()).willReturn(connectionMetaData);
		willThrow(new JMSException("Could not start", "123")).given(connection).start();
		given(connectionFactory.createConnection()).willReturn(connection);
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		Health health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).doesNotContainKey("provider");
	}

	@Test
	void whenConnectionStartThrowsWatchdogThreadDoesNotAlsoCloseConnection() throws Exception {
		Connection connection = mock(Connection.class);
		willThrow(new JMSException("Could not start", "123")).given(connection).start();
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		given(connectionFactory.createConnection()).willReturn(connection);
		JmsHealthIndicator indicator = new JmsHealthIndicator(connectionFactory);
		Health health = indicator.health(Duration.ofMillis(100));
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		then(connection).should(after(Duration.ofMillis(300).toMillis()).times(1)).close();
	}

	@Test
	void whenConnectionStartIsUnresponsiveStatusIsDown() throws JMSException {
		JmsHealthIndicator indicator = indicator(unresponsiveConnection(Connection::start));
		Health health = indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat((String) health.getDetails().get("error")).contains("Connection closed");
	}

	@Test
	@Timeout(1)
	void whenConnectionStartIsUnresponsiveClosesConnectionAfterTimeout() throws Exception {
		JmsHealthIndicator indicator = indicator(unresponsiveConnection(Connection::start));
		Health health = indicator.health(Duration.ofMillis(10));
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat((String) health.getDetails().get("error")).contains("Connection closed");
	}

	@Test
	@Timeout(1)
	void whenGetMetaDataIsUnresponsiveClosesConnectionAfterTimeout() throws Exception {
		JmsHealthIndicator indicator = indicator(unresponsiveConnection(Connection::getMetaData));
		Health health = indicator.health(Duration.ofMillis(10));
		assertThat(health).isNotNull();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat((String) health.getDetails().get("error")).contains("Connection closed");
	}

	@Test
	void whenExecutedWithTimeoutAndStartIsUnresponsiveReportsTimeoutAndClosesConnection() throws JMSException {
		Connection connection = unresponsiveConnection(Connection::start);
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("management.health.jms.timeout", Duration.ofMillis(100));
		HealthIndicatorExecutor executor = new HealthIndicatorExecutor(environment);
		try {
			Health health = executor.execute(indicator(connection), "jms", true).join();
			assertThat(health).isNotNull();
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "timeout");
			then(connection).should(timeout(1000).atLeastOnce()).close();
		}
		finally {
			executor.destroy();
		}
	}

	private Connection unresponsiveConnection(ThrowingConsumer<Connection> call) throws JMSException {
		Connection connection = mock(Connection.class);
		UnresponsiveAnswer unresponsiveAnswer = new UnresponsiveAnswer();
		call.accept(willAnswer(unresponsiveAnswer).given(connection));
		willAnswer((invocation) -> {
			unresponsiveAnswer.connectionClosed();
			return null;
		}).given(connection).close();
		return connection;
	}

	private JmsHealthIndicator indicator(Connection connection) throws JMSException {
		ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
		given(connectionFactory.createConnection()).willReturn(connection);
		return new JmsHealthIndicator(connectionFactory);
	}

	private static final class UnresponsiveAnswer implements Answer<Object> {

		private boolean connectionClosed;

		private final Object monitor = new Object();

		/**
		 * Blocks until the connection is closed, ignoring interruption like a provider
		 * which only unblocks on {@link Connection#close()}.
		 */
		@Override
		public Object answer(InvocationOnMock invocation) throws Throwable {
			boolean interrupted = false;
			synchronized (this.monitor) {
				while (!this.connectionClosed) {
					try {
						this.monitor.wait();
					}
					catch (InterruptedException ex) {
						interrupted = true;
					}
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
			throw new JMSException("Connection closed");
		}

		private void connectionClosed() {
			synchronized (this.monitor) {
				this.connectionClosed = true;
				this.monitor.notifyAll();
			}
		}

	}

}
