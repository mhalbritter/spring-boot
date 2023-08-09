/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.actuate.metrics.export.prometheus;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.PushGateway;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusPushGatewayManager.ShutdownOperation;
import org.springframework.boot.testsupport.assertj.VirtualThreadPinningAssert;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;

/**
 * Tests for {@link PrometheusPushGatewayManager}.
 *
 * @author Moritz Halbritter
 */
class PrometheusPushGatewayManagerIntegrationTests {

	private MockWebServer mockWebServer;

	private PushGateway pushGateway;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
		this.pushGateway = new PushGateway(this.mockWebServer.url("/").url());
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_21)
	void shouldNotPinThreads() throws Exception {
		VirtualThreadPinningAssert.assertThatCode(() -> {
			try (SimpleAsyncTaskScheduler scheduler = new SimpleAsyncTaskScheduler()) {
				CountingMockDispatcher dispatcher = new CountingMockDispatcher(new MockResponse());
				this.mockWebServer.setDispatcher(dispatcher);
				scheduler.setVirtualThreads(true);
				CollectorRegistry registry = new CollectorRegistry();
				Counter.build().name("counter1").help("help").register(registry);
				PrometheusPushGatewayManager manager = new PrometheusPushGatewayManager(this.pushGateway, registry,
						scheduler, Duration.ofMillis(100), "job-1", null, ShutdownOperation.PUT);
				try {
					Awaitility.await().untilAtomic(dispatcher.getRequestCount(), Matchers.greaterThanOrEqualTo(1L));
				}
				finally {
					manager.shutdown();
				}
			}
		}).doesNotPin();
	}

	private static class CountingMockDispatcher extends Dispatcher {

		private final MockResponse response;

		private final AtomicLong requestCount = new AtomicLong();

		CountingMockDispatcher(MockResponse response) {
			this.response = response;
		}

		@Override
		public MockResponse dispatch(RecordedRequest recordedRequest) {
			this.requestCount.incrementAndGet();
			return this.response;
		}

		AtomicLong getRequestCount() {
			return this.requestCount;
		}

	}

}
