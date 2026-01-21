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

import io.micrometer.opentsdb.OpenTSDBConfig;
import io.micrometer.opentsdb.OpenTSDBFlavor;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.properties.StepRegistryPropertiesConfigAdapter;

/**
 * Adapter to convert {@link OpenTSDBMetricsProperties} to an {@link OpenTSDBConfig}.
 *
 * @author Moritz Halbritter
 */
class OpenTSDBMetricsPropertiesConfigAdapter extends StepRegistryPropertiesConfigAdapter<OpenTSDBMetricsProperties>
		implements OpenTSDBConfig {

	private final OpenTSDBMetricsConnectionDetails connectionDetails;

	OpenTSDBMetricsPropertiesConfigAdapter(OpenTSDBMetricsProperties properties,
			OpenTSDBMetricsConnectionDetails connectionDetails) {
		super(properties);
		this.connectionDetails = connectionDetails;
	}

	@Override
	public String prefix() {
		return "management.opentsdb.metrics.export";
	}

	@Override
	public String uri() {
		return obtain((properties) -> this.connectionDetails.getUri(), OpenTSDBConfig.super::uri);
	}

	@Override
	public @Nullable String userName() {
		return get((properties) -> this.connectionDetails.getUsername(), OpenTSDBConfig.super::userName);
	}

	@Override
	public @Nullable String password() {
		return get((properties) -> this.connectionDetails.getPassword(), OpenTSDBConfig.super::password);
	}

	@Override
	public @Nullable OpenTSDBFlavor flavor() {
		return get((properties) -> mapFlavor(properties.getFlavor()), OpenTSDBConfig.super::flavor);
	}

	private @Nullable OpenTSDBFlavor mapFlavor(OpenTSDBMetricsProperties.@Nullable Flavor flavor) {
		if (flavor == null) {
			return null;
		}
		return switch (flavor) {
			case VICTORIA_METRICS -> OpenTSDBFlavor.VictoriaMetrics;
		};
	}

}
