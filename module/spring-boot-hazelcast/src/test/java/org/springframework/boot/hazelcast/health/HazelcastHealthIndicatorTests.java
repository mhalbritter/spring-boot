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

package org.springframework.boot.hazelcast.health;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.hazelcast.cluster.Endpoint;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionTimedOutException;
import com.hazelcast.transaction.TransactionalTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hazelcast.autoconfigure.HazelcastAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testsupport.classpath.resources.WithResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link HazelcastHealthIndicator}.
 *
 * @author Dmytro Nosan
 * @author Stephane Nicoll
 * @author Tommy Karlsson
 */
@WithResource(name = "hazelcast.xml", content = """
		<hazelcast xmlns="http://www.hazelcast.com/schema/config"
				   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				   xsi:schemaLocation="http://www.hazelcast.com/schema/config
		           http://www.hazelcast.com/schema/config/hazelcast-config-5.0.xsd">
			<instance-name>actuator-hazelcast</instance-name>
			<map name="defaultCache" />
			<network>
				<join>
					<auto-detection enabled="false"/>
					<multicast enabled="false"/>
				</join>
			</network>
		</hazelcast>
		""")
class HazelcastHealthIndicatorTests {

	@Test
	void hazelcastUp() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HazelcastAutoConfiguration.class))
			.withPropertyValues("spring.hazelcast.config=hazelcast.xml")
			.run((context) -> {
				HazelcastInstance hazelcast = context.getBean(HazelcastInstance.class);
				Health health = new HazelcastHealthIndicator(hazelcast).health();
				assertThat(health.getStatus()).isEqualTo(Status.UP);
				assertThat(health.getDetails()).containsOnlyKeys("name", "uuid")
					.containsEntry("name", "actuator-hazelcast");
				assertThat(health.getDetails().get("uuid")).asString().isNotEmpty();
			});
	}

	@Test
	void hazelcastShutdown() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HazelcastAutoConfiguration.class))
			.withPropertyValues("spring.hazelcast.config=hazelcast.xml")
			.run((context) -> {
				HazelcastInstance hazelcast = context.getBean(HazelcastInstance.class);
				hazelcast.shutdown();
				Health health = new HazelcastHealthIndicator(hazelcast).health();
				assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			});
	}

	@Test
	void hazelcastLifecycleNotRunning() {
		HazelcastInstance hazelcast = mockHazelcastInstance(false);
		Health health = new HazelcastHealthIndicator(hazelcast).health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		then(hazelcast).should().getLifecycleService();
		then(hazelcast).shouldHaveNoMoreInteractions();
	}

	@Test
	void hazelcastLifecycleNotRunningWithTimeoutDoesNotCallTransaction() throws Exception {
		HazelcastInstance hazelcast = mockHazelcastInstance(false);
		Health health = new HazelcastHealthIndicator(hazelcast).health(Duration.ofSeconds(1));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		then(hazelcast).should().getLifecycleService();
		then(hazelcast).shouldHaveNoMoreInteractions();
	}

	@Test
	void getTimeoutSupportIsNative() {
		HazelcastInstance hazelcast = mockHazelcastInstance(true);
		assertThat(new HazelcastHealthIndicator(hazelcast).getTimeoutSupport()).isEqualTo(TimeoutSupport.NATIVE);
	}

	@Test
	@SuppressWarnings("unchecked")
	void hazelcastDown() {
		HazelcastInstance hazelcast = mockHazelcastInstance(true);
		given(hazelcast.executeTransaction(any(TransactionalTask.class))).willThrow(new HazelcastException());
		Health health = new HazelcastHealthIndicator(hazelcast).health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		then(hazelcast).should().getLifecycleService();
		then(hazelcast).should().executeTransaction(any(TransactionalTask.class));
		then(hazelcast).shouldHaveNoMoreInteractions();
	}

	@Test
	@SuppressWarnings("unchecked")
	void nativeTimeoutUsesTransactionOptionsTimeout() throws Exception {
		HazelcastInstance hazelcast = mockHazelcastInstance(true);
		Endpoint endpoint = mock(Endpoint.class);
		given(endpoint.getUuid()).willReturn(UUID.randomUUID());
		given(hazelcast.getLocalEndpoint()).willReturn(endpoint);
		given(hazelcast.getName()).willReturn("mock-hazelcast");
		ArgumentCaptor<TransactionOptions> optionsCaptor = ArgumentCaptor.forClass(TransactionOptions.class);
		given(hazelcast.executeTransaction(optionsCaptor.capture(), any(TransactionalTask.class)))
			.willAnswer((invocation) -> {
				TransactionalTask<?> task = invocation.getArgument(1);
				return task.execute(null);
			});
		Health health = new HazelcastHealthIndicator(hazelcast).health(Duration.ofSeconds(2));
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsOnlyKeys("name", "uuid");
		assertThat(optionsCaptor.getValue().getTimeoutMillis()).isEqualTo(2000);
		then(hazelcast).should().getLifecycleService();
		then(hazelcast).should().executeTransaction(any(TransactionOptions.class), any(TransactionalTask.class));
		then(hazelcast).should().getLocalEndpoint();
		then(hazelcast).should().getName();
	}

	@Test
	@SuppressWarnings("unchecked")
	void transactionTimedOutExceptionMapsToTimeoutException() {
		HazelcastInstance hazelcast = mockHazelcastInstance(true);
		given(hazelcast.executeTransaction(any(TransactionOptions.class), any(TransactionalTask.class)))
			.willThrow(new TransactionTimedOutException("Transaction is timed-out!"));
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> new HazelcastHealthIndicator(hazelcast).health(Duration.ofSeconds(1)))
			.satisfies((ex) -> assertThat(ex).hasCauseInstanceOf(TransactionTimedOutException.class));
	}

	private static HazelcastInstance mockHazelcastInstance(boolean isRunning) {
		LifecycleService lifecycleService = mock(LifecycleService.class);
		given(lifecycleService.isRunning()).willReturn(isRunning);
		HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
		given(hazelcastInstance.getLifecycleService()).willReturn(lifecycleService);
		return hazelcastInstance;
	}

}
