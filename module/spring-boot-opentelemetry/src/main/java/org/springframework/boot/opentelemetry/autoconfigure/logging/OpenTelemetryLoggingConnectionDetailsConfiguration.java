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

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.OtlpExportProperties;
import org.springframework.boot.opentelemetry.autoconfigure.otlp.Transport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * {@link Configuration @Configuration} for {@link OtlpLoggingConnectionDetails}.
 *
 * @author Toshiaki Maki
 */
@Configuration(proxyBeanMethods = false)
class OpenTelemetryLoggingConnectionDetailsConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@Conditional(EndpointSetCondition.class)
	PropertiesOtlpLoggingConnectionDetails openTelemetryLoggingConnectionDetails(
			OtlpLoggingExportProperties loggingExportProperties, OtlpExportProperties exportProperties) {
		return new PropertiesOtlpLoggingConnectionDetails(loggingExportProperties, exportProperties);
	}

	/**
	 * Adapts {@link OtlpLoggingExportProperties} to {@link OtlpLoggingConnectionDetails}.
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
