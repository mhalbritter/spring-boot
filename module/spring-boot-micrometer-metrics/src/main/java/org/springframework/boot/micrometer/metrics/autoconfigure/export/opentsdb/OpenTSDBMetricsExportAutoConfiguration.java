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

import io.micrometer.core.instrument.Clock;
import io.micrometer.opentsdb.OpenTSDBConfig;
import io.micrometer.opentsdb.OpenTSDBMeterRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.ConditionalOnEnabledMetricsExport;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for exporting metrics to OpenTSDB.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
@AutoConfiguration(
		before = { CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class },
		after = MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass({ OpenTSDBMeterRegistry.class })
@ConditionalOnEnabledMetricsExport("opentsdb")
@EnableConfigurationProperties({ OpenTSDBMetricsProperties.class })
public final class OpenTSDBMetricsExportAutoConfiguration {

	private final OpenTSDBMetricsProperties properties;

	OpenTSDBMetricsExportAutoConfiguration(OpenTSDBMetricsProperties properties) {
		this.properties = properties;
	}

	@Bean
	@ConditionalOnMissingBean
	OpenTSDBMetricsConnectionDetails openTSDBMetricsConnectionDetails() {
		return new PropertiesOpenTSDBMetricsConnectionDetails(this.properties);
	}

	@Bean
	@ConditionalOnMissingBean
	OpenTSDBConfig openTSDBConfig(OpenTSDBMetricsConnectionDetails connectionDetails) {
		return new OpenTSDBMetricsPropertiesConfigAdapter(this.properties, connectionDetails);
	}

	@Bean
	@ConditionalOnMissingBean
	OpenTSDBMeterRegistry openTSDBMeterRegistry(OpenTSDBConfig openTSDBConfig, Clock clock) {
		return OpenTSDBMeterRegistry.builder(openTSDBConfig).clock(clock).build();
	}

	/**
	 * Adapts {@link OpenTSDBMetricsProperties} to
	 * {@link OpenTSDBMetricsConnectionDetails}.
	 */
	static class PropertiesOpenTSDBMetricsConnectionDetails implements OpenTSDBMetricsConnectionDetails {

		private final OpenTSDBMetricsProperties properties;

		PropertiesOpenTSDBMetricsConnectionDetails(OpenTSDBMetricsProperties properties) {
			this.properties = properties;
		}

		@Override
		public @Nullable String getUri() {
			return this.properties.getUri();
		}

		@Override
		public @Nullable String getUsername() {
			return this.properties.getUsername();
		}

		@Override
		public @Nullable String getPassword() {
			return this.properties.getPassword();
		}

	}

}
