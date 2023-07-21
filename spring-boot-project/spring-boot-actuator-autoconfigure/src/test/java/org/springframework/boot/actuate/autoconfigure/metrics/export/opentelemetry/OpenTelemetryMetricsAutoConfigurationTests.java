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

import java.util.Collection;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTelemetryMetricsAutoConfiguration}.
 *
 * @author Moritz Halbritter
 */
class OpenTelemetryMetricsAutoConfigurationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(Resource.class, Resource::empty)
		.withConfiguration(AutoConfigurations.of(OpenTelemetryMetricsAutoConfiguration.class));

	@Test
	void shouldSupplyBeans() {
		this.runner.run((context) -> assertThat(context).hasSingleBean(SdkMeterProvider.class));
	}

	@Test
	void shouldSupplyPeriodicMetricReaderIfMetricExporterIsAvailable() {
		this.runner.withBean(MetricExporter.class, NoopMetricExporter::new)
			.run((context) -> assertThat(context).hasSingleBean(PeriodicMetricReader.class));
	}

	@Test
	void shouldNotSupplyPeriodicMetricReaderIfNoMetricExporterIsAvailable() {
		this.runner.run((context) -> {
			assertThat(context).doesNotHaveBean(MetricExporter.class);
			assertThat(context).doesNotHaveBean(PeriodicMetricReader.class);
		});
	}

	@Test
	void shouldSupplyOtlpHttpMetricExporterIfPropertyIsSet() {
		this.runner
			.withPropertyValues(
					"management.opentelemetry.metrics.export.otlp.endpoint=http://localhost:4318/v1/metrics")
			.run((context) -> assertThat(context).hasSingleBean(OtlpHttpMetricExporter.class));
	}

	private static class NoopMetricExporter implements MetricExporter {

		@Override
		public CompletableResultCode export(Collection<MetricData> metrics) {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode flush() {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public CompletableResultCode shutdown() {
			return CompletableResultCode.ofSuccess();
		}

		@Override
		public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
			return AggregationTemporality.CUMULATIVE;
		}

	}

}
