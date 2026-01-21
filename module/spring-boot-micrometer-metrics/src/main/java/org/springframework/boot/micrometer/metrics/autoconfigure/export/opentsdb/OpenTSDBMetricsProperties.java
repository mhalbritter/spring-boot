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

package org.springframework.boot.micrometer.metrics.autoconfigure.export.opentsdb;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.properties.StepRegistryProperties;

/**
 * {@link ConfigurationProperties @ConfigurationProperties} for configuring OpenTSDB
 * metrics export.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
@ConfigurationProperties("management.opentsdb.metrics.export")
public class OpenTSDBMetricsProperties extends StepRegistryProperties {

	/**
	 * URI of the OpenTSDB server.
	 */
	private @Nullable String uri;

	/**
	 * Username to connect to the OpenTSDB server.
	 */
	private @Nullable String username;

	/**
	 * Password to connect to the OpenTSDB server.
	 */
	private @Nullable String password;

	/**
	 * Flavor of the OpenTSDB server.
	 */
	private @Nullable Flavor flavor;

	public @Nullable String getUri() {
		return this.uri;
	}

	public void setUri(@Nullable String uri) {
		this.uri = uri;
	}

	public @Nullable String getUsername() {
		return this.username;
	}

	public void setUsername(@Nullable String username) {
		this.username = username;
	}

	public @Nullable String getPassword() {
		return this.password;
	}

	public void setPassword(@Nullable String password) {
		this.password = password;
	}

	public @Nullable Flavor getFlavor() {
		return this.flavor;
	}

	public void setFlavor(@Nullable Flavor flavor) {
		this.flavor = flavor;
	}

	/**
	 * Flavor of the metrics.
	 */
	public enum Flavor {

		/**
		 * VictoriaMetrics.
		 */
		VICTORIA_METRICS

	}

}
