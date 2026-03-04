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

package org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.ipc.http.HttpSender.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

/**
 * Tests for {@link JdkHttpClientHttpSender}.
 *
 * @author Moritz Halbritter
 */
class JdkHttpClientHttpSenderTests {

	private MockWebServer server;

	private JdkHttpClientHttpSender sender;

	@BeforeEach
	void setUp() throws IOException {
		this.server = new MockWebServer();
		this.server.start();
		this.sender = new JdkHttpClientHttpSender(Duration.ofSeconds(5), Duration.ofSeconds(5));
	}

	@AfterEach
	void tearDown() throws IOException {
		this.server.shutdown();
	}

	@Test
	void sendShouldPerformGetRequest() throws Throwable {
		this.server.enqueue(new MockResponse());
		Response response = this.sender.get(url("/test")).send();
		assertThat(response.code()).isEqualTo(200);
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getMethod()).isEqualTo("GET");
		assertThat(request.getPath()).isEqualTo("/test");
	}

	@Test
	void sendShouldPerformPostRequest() throws Throwable {
		this.server.enqueue(new MockResponse());
		Response response = this.sender.post(url("/test")).withJsonContent("{\"key\":\"value\"}").send();
		assertThat(response.code()).isEqualTo(200);
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getMethod()).isEqualTo("POST");
		assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(request.getBody().readUtf8()).isEqualTo("{\"key\":\"value\"}");
	}

	@Test
	void sendShouldIncludeHeaders() throws Throwable {
		this.server.enqueue(new MockResponse());
		this.sender.get(url("/test")).withHeader("X-Custom-Header", "custom-value").send();
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getHeader("X-Custom-Header")).isEqualTo("custom-value");
	}

	@Test
	void sendShouldReturnResponseBody() throws Throwable {
		this.server.enqueue(new MockResponse().setBody("response-body"));
		Response response = this.sender.get(url("/test")).send();
		assertThat(response.code()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("response-body");
	}

	@Test
	void sendShouldReturnErrorStatusCode() throws Throwable {
		this.server.enqueue(new MockResponse().setResponseCode(500));
		Response response = this.sender.get(url("/test")).send();
		assertThat(response.code()).isEqualTo(500);
	}

	@Test
	void sendShouldTimeoutOnSlowResponse() {
		this.server.enqueue(new MockResponse().setHeadersDelay(5, TimeUnit.SECONDS));
		JdkHttpClientHttpSender shortTimeoutSender = new JdkHttpClientHttpSender(Duration.ofSeconds(5),
				Duration.ofMillis(10));
		assertThatIOException().isThrownBy(() -> shortTimeoutSender.get(url("/test")).send());
	}

	@Test
	void sendShouldPerformPutRequest() throws Throwable {
		this.server.enqueue(new MockResponse());
		this.sender.put(url("/test")).withPlainText("data").send();
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getMethod()).isEqualTo("PUT");
		assertThat(request.getBody().readUtf8()).isEqualTo("data");
	}

	@Test
	void sendShouldPerformDeleteRequest() throws Throwable {
		this.server.enqueue(new MockResponse());
		this.sender.delete(url("/test")).send();
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getMethod()).isEqualTo("DELETE");
	}

	private String url(String path) {
		return this.server.url(path).toString();
	}

}
