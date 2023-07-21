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

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.export.ConditionalOnEnabledMetricsExport;
import org.springframework.boot.actuate.autoconfigure.metrics.export.opentelemetry.MetricExportersConfiguration.OtlpHttpMetricExporterConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * @author Moritz Halbritter
 */
@AutoConfiguration
@ConditionalOnEnabledMetricsExport("opentelemetry")
@ConditionalOnClass(SdkMeterProvider.class)
@EnableConfigurationProperties(OpenTelemetryMetricsProperties.class)
@Import(OtlpHttpMetricExporterConfiguration.class)
class OpenTelemetryMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	SdkMeterProvider sdkMeterProvider(Resource resource, ObjectProvider<MetricReader> metricReader) {
		SdkMeterProviderBuilder builder = SdkMeterProvider.builder().setResource(resource);
		metricReader.orderedStream().forEach(builder::registerMetricReader);
		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean(MetricReader.class)
	@ConditionalOnBean(MetricExporter.class)
	PeriodicMetricReader metricReader(OpenTelemetryMetricsProperties properties, MetricExporter metricExporter) {
		return PeriodicMetricReader.builder(metricExporter).setInterval(properties.getInterval()).build();
	}

}
