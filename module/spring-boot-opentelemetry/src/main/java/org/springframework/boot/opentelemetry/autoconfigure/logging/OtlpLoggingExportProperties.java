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

package org.springframework.boot.opentelemetry.autoconfigure.logging;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.Compression;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.Transport;

/**
 * Configuration properties for exporting logs using OpenTelemetry.
 *
 * @author Jonatan Ivanov
 * @since 4.0.0
 */
@ConfigurationProperties("management.opentelemetry.logging.export.otlp")
public class OtlpLoggingExportProperties {

	/**
	 * URL to the OTel collector's HTTP API. If not set,
	 * 'management.opentelemetry.export.otlp.endpoint' is used.
	 */
	private @Nullable String endpoint;

	/**
	 * Call timeout for the OTel Collector to process an exported batch of data. This
	 * timeout spans the entire call: resolving DNS, connecting, writing the request body,
	 * server processing, and reading the response body. If the call requires redirects or
	 * retries all must complete within one timeout period. If not set,
	 * 'management.opentelemetry.export.otlp.timeout' is used.
	 */
	private @Nullable Duration timeout;

	/**
	 * Connect timeout for the OTel collector connection. If not set,
	 * 'management.opentelemetry.export.otlp.connect-timeout' is used.
	 */
	private @Nullable Duration connectTimeout;

	/**
	 * Transport used to send the logs. If not set,
	 * 'management.opentelemetry.export.otlp.transport' is used.
	 */
	private @Nullable Transport transport;

	/**
	 * Method used to compress the payload. If not set,
	 * 'management.opentelemetry.export.otlp.compression' is used.
	 */
	private @Nullable Compression compression;

	/**
	 * Custom HTTP headers you want to pass to the collector, for example auth headers.
	 * Merged with 'management.opentelemetry.export.otlp.headers'.
	 */
	private final Map<String, String> headers = new HashMap<>();

	public @Nullable String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(@Nullable String endpoint) {
		this.endpoint = endpoint;
	}

	public @Nullable Duration getTimeout() {
		return this.timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	public @Nullable Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(@Nullable Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public @Nullable Transport getTransport() {
		return this.transport;
	}

	public void setTransport(@Nullable Transport transport) {
		this.transport = transport;
	}

	public @Nullable Compression getCompression() {
		return this.compression;
	}

	public void setCompression(@Nullable Compression compression) {
		this.compression = compression;
	}

	public Map<String, String> getHeaders() {
		return this.headers;
	}

}
