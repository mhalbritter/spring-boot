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

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import org.springframework.boot.actuate.autoconfigure.metrics.export.opentelemetry.OpenTelemetryMetricsProperties.Otlp;
import org.springframework.boot.actuate.autoconfigure.metrics.export.opentelemetry.OpenTelemetryMetricsProperties.Otlp.AggregationTemporality;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Moritz Halbritter
 */
final class MetricExportersConfiguration {

	private MetricExportersConfiguration() {
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(OtlpHttpMetricExporter.class)
	@ConditionalOnMissingBean(MetricExporter.class)
	@ConditionalOnProperty(prefix = "management.opentelemetry.metrics.export.otlp", name = "endpoint")
	@EnableConfigurationProperties(OpenTelemetryMetricsProperties.class)
	static class OtlpHttpMetricExporterConfiguration {

		@Bean
		OtlpHttpMetricExporter otlpHttpMetricExporter(OpenTelemetryMetricsProperties properties) {
			Otlp otlp = properties.getOtlp();
			OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder()
				.setEndpoint(otlp.getEndpoint())
				.setCompression(otlp.getCompression().name().toLowerCase())
				.setAggregationTemporalitySelector(mapAggregationTemporality(otlp.getAggregationTemporality()))
				.setTimeout(otlp.getTimeout());
			otlp.getHeaders().forEach(builder::addHeader);
			return builder.build();
		}

		private static AggregationTemporalitySelector mapAggregationTemporality(
				AggregationTemporality aggregationTemporality) {
			return switch (aggregationTemporality) {
				case DELTA_PREFERRED -> AggregationTemporalitySelector.deltaPreferred();
				case CUMULATIVE -> AggregationTemporalitySelector.alwaysCumulative();
				case LOW_MEMORY -> AggregationTemporalitySelector.lowMemory();
			};
		}

	}

}
