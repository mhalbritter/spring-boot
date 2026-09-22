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

package org.springframework.boot.elasticsearch.health;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link ElasticsearchRestClientHealthIndicator} which exercise the
 * configured timeout against a server instead of asserting that a {@code RequestConfig}
 * was attached to the request.
 *
 * @author Moritz Halbritter
 */
class ElasticsearchRestClientHealthIndicatorIntegrationTests {

	// Large enough to never expire, even on a loaded machine
	private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration SHORT_TIMEOUT = Duration.ofMillis(200);

	private static final Duration SLOW_RESPONSE = Duration.ofSeconds(5);

	private static final String CLUSTER_HEALTH_JSON = "{\"cluster_name\":\"elasticsearch\",\"status\":\"green\"}";

	private MockWebServer server;

	private ElasticsearchRestClientHealthIndicator indicator;

	private Rest5Client client;

	@BeforeEach
	void setUp() throws IOException, URISyntaxException {
		this.server = new MockWebServer();
		this.server.start();
		this.client = Rest5Client.builder(HttpHost.create(this.server.getHostName() + ":" + this.server.getPort()))
			.build();
		this.indicator = new ElasticsearchRestClientHealthIndicator(this.client);
	}

	@AfterEach
	void tearDown() throws IOException {
		this.client.close();
		this.server.shutdown();
	}

	@Test
	void shouldBeUpWhenServerAnswersWithinTimeout() throws TimeoutException {
		this.server.enqueue(clusterHealthResponse(Duration.ZERO));
		Health health = this.indicator.health(GENEROUS_TIMEOUT);
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("status", "green");
	}

	@Test
	void shouldTimeOutWhenServerAnswersAfterTimeout() {
		this.server.enqueue(clusterHealthResponse(SLOW_RESPONSE));
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> this.indicator.health(SHORT_TIMEOUT));
	}

	@Test
	void shouldWaitForSlowServerWhenNoTimeoutIsConfigured() {
		this.server.enqueue(clusterHealthResponse(SHORT_TIMEOUT.multipliedBy(2)));
		Health health = this.indicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
	}

	private MockResponse clusterHealthResponse(Duration delay) {
		return new MockResponse().setResponseCode(200)
			.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setHeadersDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
			.setBody(CLUSTER_HEALTH_JSON);
	}

}
