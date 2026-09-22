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
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import co.elastic.clients.transport.rest5_client.low_level.Cancellable;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseListener;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpStatus;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.util.StreamUtils;

/**
 * {@link HealthIndicator} for an Elasticsearch cluster using a {@link Rest5Client}.
 * <p>
 * When a health timeout is configured, the cluster health request is sent asynchronously
 * and cancelled once the timeout has elapsed.
 *
 * @author Artsiom Yudovin
 * @author Brian Clozel
 * @author Filip Hrisafov
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class ElasticsearchRestClientHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

	private static final String CLUSTER_HEALTH_ENDPOINT = "/_cluster/health/";

	private static final String STATUS_FIELD = "status";

	private static final String RED_STATUS = "red";

	private final Rest5Client client;

	private final JsonParser jsonParser;

	public ElasticsearchRestClientHealthIndicator(Rest5Client client) {
		super(TimeoutEnforcement.INDICATOR, "Elasticsearch health check failed");
		this.client = client;
		this.jsonParser = JsonParserFactory.getJsonParser();
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception {
		Request request = new Request("GET", CLUSTER_HEALTH_ENDPOINT);
		Response response = (timeout != null) ? performAsyncRequest(request, timeout)
				: this.client.performRequest(request);
		handleResponse(builder, response);
	}

	/**
	 * Performs the given request, cancelling it once the timeout has elapsed.
	 * @param request the request to perform
	 * @param timeout the timeout to apply
	 * @return the response
	 * @throws Exception if the request failed or timed out
	 */
	private Response performAsyncRequest(Request request, Duration timeout) throws Exception {
		CompletableFuture<Response> result = new CompletableFuture<>();
		Cancellable cancellable = this.client.performRequestAsync(request, new ResponseListener() {

			@Override
			public void onSuccess(Response response) {
				result.complete(response);
			}

			@Override
			public void onFailure(Exception ex) {
				result.completeExceptionally(ex);
			}

		});
		try {
			return result.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException | InterruptedException ex) {
			cancellable.cancel();
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw ex;
		}
		catch (ExecutionException ex) {
			throw asException(ex);
		}
	}

	/**
	 * Returns the failure an {@link ExecutionException} stands for, reporting a timeout
	 * of the client itself as a {@link TimeoutException}.
	 * @param ex the execution exception
	 * @return the failure to throw
	 */
	private Exception asException(ExecutionException ex) {
		Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
		if (containsTimeoutCause(cause)) {
			String message = (cause.getMessage() != null) ? cause.getMessage() : cause.toString();
			TimeoutException timeoutException = new TimeoutException(message);
			timeoutException.initCause(cause);
			return timeoutException;
		}
		if (cause instanceof Error error) {
			throw error;
		}
		return (cause instanceof Exception exception) ? exception : ex;
	}

	private void handleResponse(Health.Builder builder, Response response) throws IOException {
		if (response.getStatusCode() != HttpStatus.SC_OK) {
			builder.down();
			builder.withDetail("statusCode", response.getStatusCode());
			builder.withDetail("warnings", response.getWarnings());
			return;
		}
		try (InputStream inputStream = response.getEntity().getContent()) {
			parseClusterHealth(builder, StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8));
		}
	}

	private void parseClusterHealth(Health.Builder builder, String json) {
		Map<String, Object> response = this.jsonParser.parseMap(json);
		String status = (String) response.get(STATUS_FIELD);
		builder.status((RED_STATUS.equals(status)) ? Status.OUT_OF_SERVICE : Status.UP);
		builder.withDetails(response);
	}

	private boolean containsTimeoutCause(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof SocketTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
