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

package org.springframework.boot.actuate.autoconfigure.metrics.export.opentelemetry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Moritz Halbritter
 */
@ConfigurationProperties(prefix = "management.opentelemetry.metrics.export")
public class OpenTelemetryMetricsProperties {

	private Duration interval = Duration.ofMinutes(1);

	private Otlp otlp;

	public Duration getInterval() {
		return this.interval;
	}

	public void setInterval(Duration interval) {
		this.interval = interval;
	}

	public Otlp getOtlp() {
		return this.otlp;
	}

	public void setOtlp(Otlp otlp) {
		this.otlp = otlp;
	}

	public static class Otlp {

		/**
		 * URL to the OTel collector's HTTP API.
		 */
		private String endpoint;

		/**
		 * Aggregation temporality of sums. It defines the way additive values are
		 * expressed. This setting depends on the backend you use, some only support one
		 * temporality.
		 */
		private AggregationTemporality aggregationTemporality = AggregationTemporality.CUMULATIVE;

		/**
		 * Headers for the exported metrics.
		 */
		private Map<String, String> headers = new HashMap<>();

		/**
		 * Call timeout for the OTel Collector to process an exported batch of data. This
		 * timeout spans the entire call: resolving DNS, connecting, writing the request
		 * body, server processing, and reading the response body. If the call requires
		 * redirects or retries all must complete within one timeout period.
		 */
		private Duration timeout = Duration.ofSeconds(10);

		/**
		 * Method used to compress the payload.
		 */
		private Compression compression = Compression.NONE;

		public String getEndpoint() {
			return this.endpoint;
		}

		public void setEndpoint(String endpoint) {
			this.endpoint = endpoint;
		}

		public AggregationTemporality getAggregationTemporality() {
			return this.aggregationTemporality;
		}

		public void setAggregationTemporality(AggregationTemporality aggregationTemporality) {
			this.aggregationTemporality = aggregationTemporality;
		}

		public Map<String, String> getHeaders() {
			return this.headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public Duration getTimeout() {
			return this.timeout;
		}

		public void setTimeout(Duration timeout) {
			this.timeout = timeout;
		}

		public Compression getCompression() {
			return this.compression;
		}

		public void setCompression(Compression compression) {
			this.compression = compression;
		}

		enum AggregationTemporality {

			DELTA_PREFERRED,

			CUMULATIVE,

			LOW_MEMORY

		}

		enum Compression {

			/**
			 * Gzip compression.
			 */
			GZIP,

			/**
			 * No compression.
			 */
			NONE

		}

	}

}
