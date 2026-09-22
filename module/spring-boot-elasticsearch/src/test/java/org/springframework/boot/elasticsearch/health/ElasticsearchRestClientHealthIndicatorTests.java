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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import co.elastic.clients.transport.rest5_client.low_level.Cancellable;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseListener;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutEnforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * Tests for {@link ElasticsearchRestClientHealthIndicator}.
 *
 * @author Artsiom Yudovin
 * @author Filip Hrisafov
 */
class ElasticsearchRestClientHealthIndicatorTests {

	private final Rest5Client restClient = mock(Rest5Client.class);

	private final ElasticsearchRestClientHealthIndicator elasticsearchRestClientHealthIndicator = new ElasticsearchRestClientHealthIndicator(
			this.restClient);

	@ParameterizedTest
	@ValueSource(strings = { "green", "yellow" })
	void shouldBeUpWhenClusterStatusIsNotRed(String clusterStatus) throws IOException {
		ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
		Response response = clusterHealthResponse(clusterStatus);
		given(this.restClient.performRequest(requestCaptor.capture())).willReturn(response);
		Health health = this.elasticsearchRestClientHealthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertHealthDetailsWithStatus(health.getDetails(), clusterStatus);
		assertThat(requestCaptor.getValue().getOptions().getRequestConfig()).isNull();
	}

	@Test
	void shouldBeDownWhenRequestFails() throws IOException {
		given(this.restClient.performRequest(any(Request.class))).willThrow(new IOException("Couldn't connect"));
		Health health = this.elasticsearchRestClientHealthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).contains(entry("error", "java.io.IOException: Couldn't connect"));
	}

	@Test
	void shouldBeDownWhenResponseCodeIsNotOk() throws IOException {
		Response response = mock(Response.class);
		given(response.getStatusCode()).willReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
		given(response.getWarnings()).willReturn(List.of("Bad things happened"));
		given(this.restClient.performRequest(any(Request.class))).willReturn(response);
		Health health = this.elasticsearchRestClientHealthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).contains(entry("statusCode", HttpStatus.SC_INTERNAL_SERVER_ERROR),
				entry("warnings", List.of("Bad things happened")));
	}

	@Test
	void shouldBeOutOfServiceWhenClusterStatusIsRed() throws IOException {
		Response response = clusterHealthResponse("red");
		given(this.restClient.performRequest(any(Request.class))).willReturn(response);
		Health health = this.elasticsearchRestClientHealthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
		assertHealthDetailsWithStatus(health.getDetails(), "red");
	}

	@Test
	void shouldEnforceTimeoutItself() {
		assertThat(this.elasticsearchRestClientHealthIndicator.getTimeoutEnforcement())
			.isEqualTo(TimeoutEnforcement.INDICATOR);
	}

	@Test
	void shouldSendRequestAsynchronouslyWhenTimeoutIsConfigured() throws Exception {
		Response response = clusterHealthResponse("green");
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willAnswer(answerWith(response));
		Health health = this.elasticsearchRestClientHealthIndicator.health(Duration.ofSeconds(5));
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertHealthDetailsWithStatus(health.getDetails(), "green");
		then(this.restClient).should(never()).performRequest(any(Request.class));
	}

	@Test
	void shouldCancelRequestWhichExceedsTimeout() {
		Cancellable cancellable = mock(Cancellable.class);
		// A request which never answers, so only the timeout ends the check.
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willReturn(cancellable);
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> this.elasticsearchRestClientHealthIndicator.health(Duration.ofMillis(50)));
		then(cancellable).should().cancel();
	}

	@Test
	void shouldReportFailureOfAsynchronousRequest() throws Exception {
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willAnswer(answerWith(new IOException("Couldn't connect")));
		Health health = this.elasticsearchRestClientHealthIndicator.health(Duration.ofSeconds(5));
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).contains(entry("error", "java.io.IOException: Couldn't connect"));
	}

	@Test
	void shouldMapSocketTimeoutToTimeoutException() throws Exception {
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willAnswer(answerWith(new SocketTimeoutException("timed out")));
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> this.elasticsearchRestClientHealthIndicator.health(Duration.ofSeconds(1)))
			.satisfies((ex) -> assertThat(ex).hasRootCauseInstanceOf(SocketTimeoutException.class));
	}

	@Test
	void shouldMapConnectTimeoutToTimeoutException() throws Exception {
		// Pins the client hierarchy the detection relies on: a ConnectTimeoutException is
		// a
		// SocketTimeoutException and therefore takes the same path.
		assertThat(ConnectTimeoutException.class).isAssignableTo(SocketTimeoutException.class);
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willAnswer(answerWith(new ConnectTimeoutException("connect timed out")));
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> this.elasticsearchRestClientHealthIndicator.health(Duration.ofSeconds(1)))
			.satisfies((ex) -> assertThat(ex).hasRootCauseInstanceOf(ConnectTimeoutException.class));
	}

	@Test
	void shouldMapWrappedSocketTimeoutToTimeoutException() throws Exception {
		IOException wrapped = new IOException("wrapper", new SocketTimeoutException("read timed out"));
		given(this.restClient.performRequestAsync(any(Request.class), any(ResponseListener.class)))
			.willAnswer(answerWith(wrapped));
		assertThatExceptionOfType(TimeoutException.class)
			.isThrownBy(() -> this.elasticsearchRestClientHealthIndicator.health(Duration.ofSeconds(1)))
			.satisfies((ex) -> assertThat(ex).hasRootCauseInstanceOf(SocketTimeoutException.class));
	}

	@Test
	void shouldBeDownWhenSocketTimesOutWithoutConfiguredTimeout() throws Exception {
		// Without a configured timeout the indicator puts no deadline on the request, so
		// a
		// timeout of the client itself is an ordinary failure, not a health timeout.
		given(this.restClient.performRequest(any(Request.class))).willThrow(new SocketTimeoutException("timed out"));
		Health health = this.elasticsearchRestClientHealthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).contains(entry("error", "java.net.SocketTimeoutException: timed out"));
	}

	private Answer<Cancellable> answerWith(Response response) {
		return (invocation) -> {
			invocation.getArgument(1, ResponseListener.class).onSuccess(response);
			return mock(Cancellable.class);
		};
	}

	private Answer<Cancellable> answerWith(Exception failure) {
		return (invocation) -> {
			invocation.getArgument(1, ResponseListener.class).onFailure(failure);
			return mock(Cancellable.class);
		};
	}

	private Response clusterHealthResponse(String clusterStatus) {
		BasicHttpEntity httpEntity = new BasicHttpEntity(
				new ByteArrayInputStream(createJsonResult(HttpStatus.SC_OK, clusterStatus).getBytes()),
				ContentType.APPLICATION_JSON);
		Response response = mock(Response.class);
		given(response.getStatusCode()).willReturn(HttpStatus.SC_OK);
		given(response.getEntity()).willReturn(httpEntity);
		return response;
	}

	private void assertHealthDetailsWithStatus(Map<String, Object> details, String status) {
		assertThat(details).contains(entry("cluster_name", "elasticsearch"), entry("status", status),
				entry("timed_out", false), entry("number_of_nodes", 1), entry("number_of_data_nodes", 1),
				entry("active_primary_shards", 0), entry("active_shards", 0), entry("relocating_shards", 0),
				entry("initializing_shards", 0), entry("unassigned_shards", 0), entry("delayed_unassigned_shards", 0),
				entry("number_of_pending_tasks", 0), entry("number_of_in_flight_fetch", 0),
				entry("task_max_waiting_in_queue_millis", 0), entry("active_shards_percent_as_number", 100.0),
				entry("unassigned_primary_shards", 10));
	}

	private String createJsonResult(int responseCode, String status) {
		if (responseCode == HttpStatus.SC_OK) {
			return String.format("{\"cluster_name\":\"elasticsearch\","
					+ "\"status\":\"%s\",\"timed_out\":false,\"number_of_nodes\":1,"
					+ "\"number_of_data_nodes\":1,\"active_primary_shards\":0,"
					+ "\"active_shards\":0,\"relocating_shards\":0,\"initializing_shards\":0,"
					+ "\"unassigned_shards\":0,\"delayed_unassigned_shards\":0,"
					+ "\"number_of_pending_tasks\":0,\"number_of_in_flight_fetch\":0,"
					+ "\"task_max_waiting_in_queue_millis\":0,\"active_shards_percent_as_number\":100.0,"
					+ "\"unassigned_primary_shards\": 10 }", status);
		}
		return "{\n  \"error\": \"Server Error\",\n  \"status\": " + responseCode + "\n}";
	}

}
