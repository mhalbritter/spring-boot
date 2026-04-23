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
import java.util.concurrent.TimeoutException;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.RequestOptions;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.util.StreamUtils;

/**
 * {@link HealthIndicator} for an Elasticsearch cluster using a {@link Rest5Client}.
 * <p>
 * When a health timeout is configured, it is applied on the request for the connection
 * lease request and the response.
 *
 * @author Artsiom Yudovin
 * @author Brian Clozel
 * @author Filip Hrisafov
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class ElasticsearchRestClientHealthIndicator extends AbstractHealthIndicator {

	private static final String CLUSTER_HEALTH_ENDPOINT = "/_cluster/health/";

	private static final String RED_STATUS = "red";

	private final Rest5Client client;

	private final JsonParser jsonParser;

	public ElasticsearchRestClientHealthIndicator(Rest5Client client) {
		super("Elasticsearch health check failed");
		this.client = client;
		this.jsonParser = JsonParserFactory.getJsonParser();
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		Response response = this.client.performRequest(new Request("GET", CLUSTER_HEALTH_ENDPOINT));
		handleResponse(builder, response);
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		Request request = createClusterHealthRequest(timeout);
		try {
			handleResponse(builder, this.client.performRequest(request));
		}
		catch (SocketTimeoutException ex) {
			throw createTimeoutException(ex);
		}
		catch (IOException ex) {
			if (containsTimeoutCause(ex)) {
				throw createTimeoutException(ex);
			}
			throw ex;
		}
	}

	private Request createClusterHealthRequest(Duration timeout) {
		Request request = new Request("GET", CLUSTER_HEALTH_ENDPOINT);
		Timeout clientTimeout = Timeout.ofMilliseconds(toTimeoutMillis(timeout));
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout(clientTimeout)
			.setResponseTimeout(clientTimeout)
			.build();
		request.setOptions(RequestOptions.DEFAULT.toBuilder().setRequestConfig(requestConfig).build());
		return request;
	}

	/**
	 * Converts a {@link Duration} to a positive HTTP client timeout in whole milliseconds
	 * (minimum {@code 1}).
	 * @param timeout the timeout
	 * @return timeout in milliseconds
	 */
	private long toTimeoutMillis(Duration timeout) {
		long millis = timeout.toMillis();
		return Math.max(1, millis);
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
		String status = (String) response.get("status");
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

	private TimeoutException createTimeoutException(Exception cause) {
		TimeoutException timeoutException = new TimeoutException(cause.getMessage());
		timeoutException.initCause(cause);
		return timeoutException;
	}

}
