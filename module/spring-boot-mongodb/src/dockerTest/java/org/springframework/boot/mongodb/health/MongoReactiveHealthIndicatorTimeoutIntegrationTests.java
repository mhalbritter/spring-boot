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

package org.springframework.boot.mongodb.health;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.test.StepVerifier;

import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.testsupport.container.TestImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for timeout handling of {@link MongoReactiveHealthIndicator}.
 *
 * @author Moritz Halbritter
 */
@Testcontainers(disabledWithoutDocker = true)
@Timeout(value = 5, threadMode = ThreadMode.SEPARATE_THREAD)
class MongoReactiveHealthIndicatorTimeoutIntegrationTests {

	private static final Duration TIMEOUT = Duration.ofMillis(500);

	private static final Duration MARGIN = Duration.ofMillis(1500);

	@Container
	static MongoDBContainer mongo = TestImage.forContainer(MongoDBContainer.class);

	private @Nullable MongoClient client;

	@AfterEach
	void cleanUp() {
		if (isPaused()) {
			mongo.getDockerClient().unpauseContainerCmd(mongo.getContainerId()).exec();
		}
		if (this.client != null) {
			this.client.close();
		}
	}

	@Test
	void shouldTimeOutWhenServerHangsAfterConnecting() {
		MongoReactiveHealthIndicator indicator = new MongoReactiveHealthIndicator(createClient());
		StepVerifier.create(indicator.health())
			.assertNext((health) -> assertThat(health.getStatus()).isEqualTo(Status.UP))
			.verifyComplete();
		pause();
		assertTimesOut(indicator);
	}

	@Test
	void shouldTimeOutWhenServerHangsBeforeConnecting() {
		MongoReactiveHealthIndicator indicator = new MongoReactiveHealthIndicator(createClient());
		pause();
		assertTimesOut(indicator);
	}

	private void assertTimesOut(MongoReactiveHealthIndicator indicator) {
		long start = System.nanoTime();
		StepVerifier.create(indicator.health(TIMEOUT)).expectError(TimeoutException.class).verify();
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
		assertThat(elapsed).isLessThan(TIMEOUT.plus(MARGIN));
	}

	private MongoClient createClient() {
		this.client = MongoClients.create(mongo.getConnectionString());
		return this.client;
	}

	private void pause() {
		mongo.getDockerClient().pauseContainerCmd(mongo.getContainerId()).exec();
	}

	private boolean isPaused() {
		return Boolean.TRUE
			.equals(mongo.getDockerClient().inspectContainerCmd(mongo.getContainerId()).exec().getState().getPaused());
	}

}
