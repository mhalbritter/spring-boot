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

package org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Builder;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.micrometer.tracing.autoconfigure.ConditionalOnEnabledTracingExport;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.OtlpExportProperties;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;

/**
 * Configurations imported by {@link OtlpTracingAutoConfiguration}.
 *
 * @author Moritz Halbritter
 * @author Eddú Meléndez
 */
final class OtlpTracingConfigurations {

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetails {

		@Bean
		@ConditionalOnMissingBean
		@Conditional(EndpointSetCondition.class)
		OtlpTracingConnectionDetails otlpTracingConnectionDetails(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties) {
			return new PropertiesOtlpTracingConnectionDetails(tracingExportProperties, exportProperties);
		}

		/**
		 * Adapts {@link OtlpTracingExportProperties} to
		 * {@link OtlpTracingConnectionDetails}.
		 */
		static class PropertiesOtlpTracingConnectionDetails implements OtlpTracingConnectionDetails {

			private final OtlpTracingExportProperties tracingExportProperties;

			private final OtlpExportProperties exportProperties;

			PropertiesOtlpTracingConnectionDetails(OtlpTracingExportProperties tracingExportProperties,
					OtlpExportProperties exportProperties) {
				this.tracingExportProperties = tracingExportProperties;
				this.exportProperties = exportProperties;
			}

			@Override
			public String getUrl(Transport transport) {
				Assert.state(transport == getTransport(), "Requested transport %s doesn't match configured transport %s"
					.formatted(transport, getTransport()));
				return getEndpoint();
			}

			private String getEndpoint() {
				if (this.tracingExportProperties.getEndpoint() != null) {
					return this.tracingExportProperties.getEndpoint();
				}
				String endpoint = this.exportProperties.getEndpoint();
				Assert.state(endpoint != null, "'endpoint' must not be null");
				return endpoint;
			}

			private Transport getTransport() {
				if (this.tracingExportProperties.getTransport() != null) {
					return this.tracingExportProperties.getTransport();
				}
				return this.exportProperties.getTransport();
			}

		}

		static class EndpointSetCondition extends AnyNestedCondition {

			EndpointSetCondition() {
				super(ConfigurationPhase.REGISTER_BEAN);
			}

			@ConditionalOnProperty("management.opentelemetry.tracing.export.otlp.endpoint")
			static class OtlpTracingEndpoint {

			}

			@ConditionalOnProperty("management.opentelemetry.export.otlp.endpoint")
			static class OtlpEndpoint {

			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean({ OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class })
	@ConditionalOnBean(OtlpTracingConnectionDetails.class)
	@ConditionalOnEnabledTracingExport("otlp")
	static class Exporters {

		@Bean
		@Conditional(HttpTransportPropertyCondition.class)
		OtlpHttpSpanExporter otlpHttpSpanExporter(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties, OtlpTracingConnectionDetails connectionDetails,
				ObjectProvider<MeterProvider> meterProvider,
				ObjectProvider<OtlpHttpSpanExporterBuilderCustomizer> customizers) {
			OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder()
				.setEndpoint(connectionDetails.getUrl(Transport.HTTP))
				.setTimeout(getTimeout(tracingExportProperties, exportProperties))
				.setConnectTimeout(getConnectTimeout(tracingExportProperties, exportProperties))
				.setCompression(getCompression(tracingExportProperties, exportProperties));
			getHeaders(tracingExportProperties, exportProperties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
			return builder.build();
		}

		@Bean
		@Conditional(GrpcTransportPropertyCondition.class)
		OtlpGrpcSpanExporter otlpGrpcSpanExporter(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties, OtlpTracingConnectionDetails connectionDetails,
				ObjectProvider<MeterProvider> meterProvider,
				ObjectProvider<OtlpGrpcSpanExporterBuilderCustomizer> customizers) {
			OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder()
				.setEndpoint(connectionDetails.getUrl(Transport.GRPC))
				.setTimeout(getTimeout(tracingExportProperties, exportProperties))
				.setConnectTimeout(getConnectTimeout(tracingExportProperties, exportProperties))
				.setCompression(getCompression(tracingExportProperties, exportProperties));
			getHeaders(tracingExportProperties, exportProperties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
			return builder.build();
		}

		private Map<String, String> getHeaders(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties) {
			Map<String, String> headers = new HashMap<>(exportProperties.getHeaders());
			headers.putAll(tracingExportProperties.getHeaders());
			return headers;
		}

		private String getCompression(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties) {
			if (tracingExportProperties.getCompression() != null) {
				return tracingExportProperties.getCompression().name().toLowerCase(Locale.ROOT);
			}
			return exportProperties.getCompression().name().toLowerCase(Locale.ROOT);
		}

		private Duration getConnectTimeout(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties) {
			if (tracingExportProperties.getConnectTimeout() != null) {
				return tracingExportProperties.getConnectTimeout();
			}
			return exportProperties.getConnectTimeout();
		}

		private Duration getTimeout(OtlpTracingExportProperties tracingExportProperties,
				OtlpExportProperties exportProperties) {
			if (tracingExportProperties.getTimeout() != null) {
				return tracingExportProperties.getTimeout();
			}
			return exportProperties.getTimeout();
		}

		private abstract static class TransportPropertyCondition extends SpringBootCondition {

			private static final Transport DEFAULT_TRANSPORT = Transport.HTTP;

			private final Transport expectedTransport;

			TransportPropertyCondition(Transport expectedTransport) {
				this.expectedTransport = expectedTransport;
			}

			@Override
			public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
				String matchedProperty = getMatchedProperty(context.getEnvironment());
				String expectedTransportName = this.expectedTransport.name().toLowerCase(Locale.ROOT);
				Builder messageBuilder = ConditionMessage.forCondition(ConditionalOnProperty.class);
				if (matchedProperty == null) {
					if (this.expectedTransport == DEFAULT_TRANSPORT) {
						return ConditionOutcome.match(messageBuilder.because("The default value of '%s' matches '%s'"
							.formatted(OtlpExportProperties.PREFIX + ".transport", expectedTransportName)));
					}
					return ConditionOutcome
						.noMatch(messageBuilder.because("The default value of '%s' doesn't match '%s'"
							.formatted(OtlpExportProperties.PREFIX + ".transport", expectedTransportName)));
				}
				String propertyValue = context.getEnvironment()
					.getProperty(matchedProperty, DEFAULT_TRANSPORT.name().toLowerCase(Locale.ROOT));
				if (propertyValue.equals(expectedTransportName)) {
					return ConditionOutcome.match(messageBuilder
						.because("'%s' matches '%s'".formatted(matchedProperty, expectedTransportName)));
				}
				return ConditionOutcome.noMatch(messageBuilder
					.because("'%s' doesn't match '%s'".formatted(matchedProperty, expectedTransportName)));
			}

			private @Nullable String getMatchedProperty(Environment environment) {
				if (environment.containsProperty(OtlpTracingExportProperties.PREFIX + ".transport")) {
					return OtlpTracingExportProperties.PREFIX + ".transport";
				}
				if (environment.containsProperty(OtlpExportProperties.PREFIX + ".transport")) {
					return OtlpExportProperties.PREFIX + ".transport";
				}
				return null;
			}

		}

		private static class HttpTransportPropertyCondition extends TransportPropertyCondition {

			HttpTransportPropertyCondition() {
				super(Transport.HTTP);
			}

		}

		private static class GrpcTransportPropertyCondition extends TransportPropertyCondition {

			GrpcTransportPropertyCondition() {
				super(Transport.GRPC);
			}

		}

	}

}
