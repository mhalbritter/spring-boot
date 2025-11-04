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
import java.util.Locale;
import java.util.Map;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.logging.ConditionalOnEnabledLoggingExport;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.OtlpExportProperties;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * Configurations imported by {@link OtlpLoggingExportAutoConfiguration}.
 *
 * @author Moritz Halbritter
 * @author Toshiaki Maki
 */
class OtlpLoggingConfigurations {

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetails {

		@Bean
		@ConditionalOnMissingBean
		@Conditional(EndpointSetCondition.class)
		PropertiesOtlpLoggingConnectionDetails openTelemetryLoggingConnectionDetails(
				OtlpLoggingExportProperties loggingExportProperties, OtlpExportProperties exportProperties) {
			return new PropertiesOtlpLoggingConnectionDetails(loggingExportProperties, exportProperties);
		}

		/**
		 * Adapts {@link OtlpLoggingExportProperties} to
		 * {@link OtlpLoggingConnectionDetails}.
		 */
		static class PropertiesOtlpLoggingConnectionDetails implements OtlpLoggingConnectionDetails {

			private final OtlpLoggingExportProperties loggingExportProperties;

			private final OtlpExportProperties exportProperties;

			PropertiesOtlpLoggingConnectionDetails(OtlpLoggingExportProperties loggingExportProperties,
					OtlpExportProperties exportProperties) {
				this.loggingExportProperties = loggingExportProperties;
				this.exportProperties = exportProperties;
			}

			@Override
			public String getUrl(Transport transport) {
				Assert.state(transport == getTransport(), "Requested transport %s doesn't match configured transport %s"
					.formatted(transport, getTransport()));
				return getEndpoint();
			}

			private String getEndpoint() {
				if (this.loggingExportProperties.getEndpoint() != null) {
					return this.loggingExportProperties.getEndpoint();
				}
				String endpoint = this.exportProperties.getEndpoint();
				Assert.state(endpoint != null, "'endpoint' must not be null");
				return endpoint;
			}

			private Transport getTransport() {
				if (this.loggingExportProperties.getTransport() != null) {
					return this.loggingExportProperties.getTransport();
				}
				return this.exportProperties.getTransport();
			}

		}

		static class EndpointSetCondition extends AnyNestedCondition {

			EndpointSetCondition() {
				super(ConfigurationPhase.REGISTER_BEAN);
			}

			@ConditionalOnProperty("management.opentelemetry.export.otlp.endpoint")
			static class OtlpEndpoint {

			}

			@ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
			static class OtlpTracingEndpoint {

			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ OtlpHttpLogRecordExporter.class, ConditionalOnEnabledLoggingExport.class })
	@ConditionalOnMissingBean({ OtlpGrpcLogRecordExporter.class, OtlpHttpLogRecordExporter.class })
	@ConditionalOnBean(OtlpLoggingConnectionDetails.class)
	@ConditionalOnEnabledLoggingExport("otlp")
	static class Exporters {

		@Bean
		@ConditionalOnProperty(name = "management.opentelemetry.logging.export.otlp.transport", havingValue = "http",
				matchIfMissing = true)
		OtlpHttpLogRecordExporter otlpHttpLogRecordExporter(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties, OtlpLoggingConnectionDetails connectionDetails,
				ObjectProvider<MeterProvider> meterProvider) {
			OtlpHttpLogRecordExporterBuilder builder = OtlpHttpLogRecordExporter.builder()
				.setEndpoint(connectionDetails.getUrl(Transport.HTTP))
				.setTimeout(getTimeout(otlpLoggingExportProperties, exportProperties))
				.setConnectTimeout(getConnectTimeout(otlpLoggingExportProperties, exportProperties))
				.setCompression(getCompression(otlpLoggingExportProperties, exportProperties));
			getHeaders(otlpLoggingExportProperties, exportProperties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			return builder.build();
		}

		@Bean
		@ConditionalOnProperty(name = "management.opentelemetry.logging.export.otlp.transport", havingValue = "grpc")
		OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties, OtlpLoggingConnectionDetails connectionDetails,
				ObjectProvider<MeterProvider> meterProvider) {
			OtlpGrpcLogRecordExporterBuilder builder = OtlpGrpcLogRecordExporter.builder()
				.setEndpoint(connectionDetails.getUrl(Transport.GRPC))
				.setTimeout(getTimeout(otlpLoggingExportProperties, exportProperties))
				.setConnectTimeout(getConnectTimeout(otlpLoggingExportProperties, exportProperties))
				.setCompression(getCompression(otlpLoggingExportProperties, exportProperties));
			getHeaders(otlpLoggingExportProperties, exportProperties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			return builder.build();
		}

		private Map<String, String> getHeaders(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties) {
			Map<String, String> headers = new HashMap<>(exportProperties.getHeaders());
			headers.putAll(otlpLoggingExportProperties.getHeaders());
			return headers;
		}

		private String getCompression(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties) {
			if (otlpLoggingExportProperties.getCompression() != null) {
				return otlpLoggingExportProperties.getCompression().name().toLowerCase(Locale.US);
			}
			return exportProperties.getCompression().name().toLowerCase(Locale.US);
		}

		private Duration getConnectTimeout(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties) {
			if (otlpLoggingExportProperties.getConnectTimeout() != null) {
				return otlpLoggingExportProperties.getConnectTimeout();
			}
			return exportProperties.getConnectTimeout();
		}

		private Duration getTimeout(OtlpLoggingExportProperties otlpLoggingExportProperties,
				OtlpExportProperties exportProperties) {
			if (otlpLoggingExportProperties.getTimeout() != null) {
				return otlpLoggingExportProperties.getTimeout();
			}
			return exportProperties.getTimeout();
		}

	}

}
