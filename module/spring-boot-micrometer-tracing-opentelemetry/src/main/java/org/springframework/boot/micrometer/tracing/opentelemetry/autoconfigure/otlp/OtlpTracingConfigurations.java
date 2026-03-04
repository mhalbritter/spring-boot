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
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.micrometer.tracing.autoconfigure.ConditionalOnEnabledTracingExport;
import org.springframework.boot.opentelemetry.OpenTelemetryEnvironmentVariables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
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
		@ConditionalOnProperty("management.opentelemetry.tracing.export.otlp.endpoint")
		OtlpTracingConnectionDetails otlpTracingConnectionDetails(OtlpTracingProperties properties) {
			return new PropertiesOtlpTracingConnectionDetails(properties);
		}

		/**
		 * Adapts {@link OtlpTracingProperties} to {@link OtlpTracingConnectionDetails}.
		 */
		static class PropertiesOtlpTracingConnectionDetails implements OtlpTracingConnectionDetails {

			private final OtlpTracingProperties properties;

			PropertiesOtlpTracingConnectionDetails(OtlpTracingProperties properties) {
				this.properties = properties;
			}

			@Override
			public String getUrl(Transport transport) {
				String endpoint = this.properties.getEndpoint();
				Assert.state(endpoint != null, "'endpoint' must not be null");
				return endpoint;
			}

		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean({ OtlpGrpcSpanExporter.class, OtlpHttpSpanExporter.class })
	@ConditionalOnEnabledTracingExport("otlp")
	static class Exporters {

		@Bean
		@Conditional(OnHttpOtlpTransportCondition.class)
		@Nullable OtlpHttpSpanExporter otlpHttpSpanExporter(OtlpTracingProperties properties,
				@Nullable OtlpTracingConnectionDetails connectionDetails, ObjectProvider<MeterProvider> meterProvider,
				ObjectProvider<OtlpHttpSpanExporterBuilderCustomizer> customizers,
				ObjectProvider<OpenTelemetryEnvironmentVariables> envVariablesProvider) {
			OpenTelemetryEnvironmentVariables envVariables = envVariablesProvider
				.getIfAvailable(OpenTelemetryEnvironmentVariables::fromSystemEnv);
			if (!hasEndpoint(envVariables, connectionDetails)) {
				// TODO MH: I don't think we return null from any bean method in Spring
				// Boot, is this ok?
				return null;
			}
			OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder()
				.setEndpoint(getEndpoint(envVariables, connectionDetails, Transport.HTTP))
				.setTimeout(getTimeout(envVariables, properties))
				.setConnectTimeout(properties.getConnectTimeout())
				.setCompression(getCompression(envVariables, properties));
			byte[] trustedCertificates = getTrustedCertificates(envVariables);
			if (trustedCertificates != null) {
				builder.setTrustedCertificates(trustedCertificates);
			}
			byte[] clientCertificate = getClientCertificate(envVariables);
			byte[] clientKey = getClientKey(envVariables);
			if (clientCertificate != null && clientKey != null) {
				builder.setClientTls(clientKey, clientCertificate);
			}
			getHeaders(envVariables, properties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
			return builder.build();
		}

		@Bean
		@Conditional(OnGrpcOtlpTransportCondition.class)
		@Nullable OtlpGrpcSpanExporter otlpGrpcSpanExporter(OtlpTracingProperties properties,
				@Nullable OtlpTracingConnectionDetails connectionDetails, ObjectProvider<MeterProvider> meterProvider,
				ObjectProvider<OtlpGrpcSpanExporterBuilderCustomizer> customizers,
				ObjectProvider<OpenTelemetryEnvironmentVariables> envVariablesProvider) {
			OpenTelemetryEnvironmentVariables envVariables = envVariablesProvider
				.getIfAvailable(OpenTelemetryEnvironmentVariables::fromSystemEnv);
			if (!hasEndpoint(envVariables, connectionDetails)) {
				// TODO MH: I don't think we return null from any bean method in Spring
				// Boot, is this ok?
				return null;
			}
			OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder()
				.setEndpoint(getEndpoint(envVariables, connectionDetails, Transport.GRPC))
				.setTimeout(getTimeout(envVariables, properties))
				.setConnectTimeout(properties.getConnectTimeout())
				.setCompression(getCompression(envVariables, properties));
			byte[] trustedCertificates = getTrustedCertificates(envVariables);
			if (trustedCertificates != null) {
				builder.setTrustedCertificates(trustedCertificates);
			}
			byte[] clientCertificate = getClientCertificate(envVariables);
			byte[] clientKey = getClientKey(envVariables);
			if (clientCertificate != null && clientKey != null) {
				builder.setClientTls(clientKey, clientCertificate);
			}
			getHeaders(envVariables, properties).forEach(builder::addHeader);
			meterProvider.ifAvailable(builder::setMeterProvider);
			customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
			return builder.build();
		}

		private boolean hasEndpoint(OpenTelemetryEnvironmentVariables envVariables,
				@Nullable OtlpTracingConnectionDetails connectionDetails) {
			if (envVariables.getString("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT") != null
					|| envVariables.getString("OTEL_EXPORTER_OTLP_ENDPOINT") != null) {
				return true;
			}
			return connectionDetails != null;
		}

		private Duration getTimeout(OpenTelemetryEnvironmentVariables envVariables, OtlpTracingProperties properties) {
			Duration tracesTimeout = envVariables.getTimeout("OTEL_EXPORTER_OTLP_TRACES_TIMEOUT");
			if (tracesTimeout != null) {
				return tracesTimeout;
			}
			Duration generalTimeout = envVariables.getTimeout("OTEL_EXPORTER_OTLP_TIMEOUT");
			if (generalTimeout != null) {
				return generalTimeout;
			}
			return properties.getTimeout();
		}

		private String getCompression(OpenTelemetryEnvironmentVariables envVariables,
				OtlpTracingProperties properties) {
			String tracesCompression = envVariables.getString("OTEL_EXPORTER_OTLP_TRACES_COMPRESSION");
			if (tracesCompression != null) {
				return tracesCompression;
			}
			String generalCompression = envVariables.getString("OTEL_EXPORTER_OTLP_COMPRESSION");
			if (generalCompression != null) {
				return generalCompression;
			}
			return properties.getCompression().name().toLowerCase(Locale.ROOT);
		}

		private Map<String, String> getHeaders(OpenTelemetryEnvironmentVariables envVariables,
				OtlpTracingProperties properties) {
			Map<String, String> tracesHeaders = envVariables.getHeaders("OTEL_EXPORTER_OTLP_TRACES_HEADERS");
			if (tracesHeaders != null) {
				return tracesHeaders;
			}
			Map<String, String> generalHeaders = envVariables.getHeaders("OTEL_EXPORTER_OTLP_HEADERS");
			if (generalHeaders != null) {
				return generalHeaders;
			}
			return properties.getHeaders();
		}

		private byte @Nullable [] getTrustedCertificates(OpenTelemetryEnvironmentVariables envVariables) {
			byte[] tracesCertificates = envVariables.getFileContent("OTEL_EXPORTER_OTLP_TRACES_CERTIFICATE");
			return (tracesCertificates != null) ? tracesCertificates
					: envVariables.getFileContent("OTEL_EXPORTER_OTLP_CERTIFICATE");
		}

		private byte @Nullable [] getClientKey(OpenTelemetryEnvironmentVariables envVariables) {
			byte[] tracesClientKey = envVariables.getFileContent("OTEL_EXPORTER_OTLP_TRACES_CLIENT_KEY");
			return (tracesClientKey != null) ? tracesClientKey
					: envVariables.getFileContent("OTEL_EXPORTER_OTLP_CLIENT_KEY");
		}

		private byte @Nullable [] getClientCertificate(OpenTelemetryEnvironmentVariables envVariables) {
			byte[] tracesClientCertificate = envVariables
				.getFileContent("OTEL_EXPORTER_OTLP_TRACES_CLIENT_CERTIFICATE");
			return (tracesClientCertificate != null) ? tracesClientCertificate
					: envVariables.getFileContent("OTEL_EXPORTER_OTLP_CLIENT_CERTIFICATE");
		}

		private String getEndpoint(OpenTelemetryEnvironmentVariables envVariables,
				@Nullable OtlpTracingConnectionDetails connectionDetails, Transport transport) {
			String tracesEndpoint = envVariables.getString("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT");
			if (tracesEndpoint != null) {
				return tracesEndpoint;
			}
			String generalEndpoint = envVariables.getString("OTEL_EXPORTER_OTLP_ENDPOINT");
			if (generalEndpoint != null) {
				return combineUrl(generalEndpoint, "v1/traces");
			}
			Assert.state(connectionDetails != null, "'connectionDetails' must not be null");
			return connectionDetails.getUrl(transport);
		}

		private String combineUrl(String endpoint, String path) {
			if (endpoint.endsWith("/")) {
				return endpoint + path;
			}
			return endpoint + "/" + path;
		}

		private static class AbstractOnOtlpTransportCondition extends SpringBootCondition {

			private static final String SPECIFIC_ENVIRONMENT_VARIABLE_NAME = "OTEL_EXPORTER_OTLP_TRACES_PROTOCOL";

			private static final String GENERIC_ENVIRONMENT_VARIABLE_NAME = "OTEL_EXPORTER_OTLP_PROTOCOL";

			private static final String PROPERTY_NAME = "management.opentelemetry.tracing.export.otlp.transport";

			private final Function<String, @Nullable String> envLookup;

			private final String environmentVariableValue;

			private final boolean matchIfMissing;

			private final Transport propertyValue;

			AbstractOnOtlpTransportCondition(Function<String, @Nullable String> envLookup,
					String environmentVariableValue, Transport propertyValue, boolean matchIfMissing) {
				this.envLookup = envLookup;
				this.environmentVariableValue = environmentVariableValue;
				this.propertyValue = propertyValue;
				this.matchIfMissing = matchIfMissing;
			}

			@Override
			public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
				String tracesProtocol = this.envLookup.apply(SPECIFIC_ENVIRONMENT_VARIABLE_NAME);
				if (tracesProtocol != null) {
					String message = "Environment variable %s is set to %s"
						.formatted(SPECIFIC_ENVIRONMENT_VARIABLE_NAME, tracesProtocol);
					if (tracesProtocol.equals(this.environmentVariableValue)) {
						return ConditionOutcome.match(message);
					}
					else {
						return ConditionOutcome.noMatch(message);
					}
				}
				String generalProtocol = this.envLookup.apply(GENERIC_ENVIRONMENT_VARIABLE_NAME);
				if (generalProtocol != null) {
					String message = "Environment variable %s is set to %s".formatted(GENERIC_ENVIRONMENT_VARIABLE_NAME,
							generalProtocol);
					if (generalProtocol.equals(this.environmentVariableValue)) {
						return ConditionOutcome.match(message);
					}
					else {
						return ConditionOutcome.noMatch(message);
					}
				}
				String property = context.getEnvironment().getProperty(PROPERTY_NAME);
				if (property == null) {
					if (this.matchIfMissing) {
						return ConditionOutcome
							.match("Property %s defaults to %s".formatted(PROPERTY_NAME, this.propertyValue.name()));
					}
					else {
						return ConditionOutcome.noMatch("Property %s isn't set".formatted(PROPERTY_NAME));
					}
				}
				else {
					String message = "Property %s is set to %s".formatted(PROPERTY_NAME, property);
					if (property.equalsIgnoreCase(this.propertyValue.name())) {
						return ConditionOutcome.match(message);
					}
					else {
						return ConditionOutcome.noMatch(message);
					}
				}
			}

		}

		static class OnHttpOtlpTransportCondition extends AbstractOnOtlpTransportCondition {

			OnHttpOtlpTransportCondition() {
				this(System::getenv);
			}

			OnHttpOtlpTransportCondition(Function<String, @Nullable String> envLookup) {
				super(envLookup, "http/protobuf", Transport.HTTP, true);
			}

		}

		static class OnGrpcOtlpTransportCondition extends AbstractOnOtlpTransportCondition {

			OnGrpcOtlpTransportCondition() {
				this(System::getenv);
			}

			OnGrpcOtlpTransportCondition(Function<String, @Nullable String> envLookup) {
				super(envLookup, "grpc", Transport.GRPC, false);
			}

		}

	}

}
