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
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.opentsdb.OpenTSDBMetricsExportAutoConfiguration.PropertiesOpenTSDBMetricsConnectionDetails;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTSDBMetricsExportAutoConfiguration}.
 *
 * @author Moritz Halbritter
 */
class OpenTSDBMetricsExportAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenTSDBMetricsExportAutoConfiguration.class));

	@Test
	void backsOffWithoutAClock() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(OpenTSDBMeterRegistry.class));
	}

	@Test
	void autoConfiguresConfigAndMeterRegistry() {
		this.contextRunner.withUserConfiguration(BaseConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(OpenTSDBMeterRegistry.class)
				.hasSingleBean(OpenTSDBConfig.class));
	}

	@Test
	void autoConfigurationCanBeDisabledWithDefaultsEnabledProperty() {
		this.contextRunner.withUserConfiguration(BaseConfiguration.class)
			.withPropertyValues("management.defaults.metrics.export.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(OpenTSDBMeterRegistry.class)
				.doesNotHaveBean(OpenTSDBConfig.class));
	}

	@Test
	void autoConfigurationCanBeDisabledWithSpecificEnabledProperty() {
		this.contextRunner.withUserConfiguration(BaseConfiguration.class)
			.withPropertyValues("management.opentsdb.metrics.export.enabled=false")
			.run((context) -> assertThat(context).doesNotHaveBean(OpenTSDBMeterRegistry.class)
				.doesNotHaveBean(OpenTSDBConfig.class));
	}

	@Test
	void allowsCustomConfigToBeUsed() {
		this.contextRunner.withUserConfiguration(CustomConfigConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(OpenTSDBMeterRegistry.class)
				.hasSingleBean(OpenTSDBConfig.class)
				.hasBean("customConfig"));
	}

	@Test
	void allowsRegistryToBeCustomized() {
		this.contextRunner.withUserConfiguration(CustomRegistryConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(OpenTSDBMeterRegistry.class)
				.hasSingleBean(OpenTSDBConfig.class)
				.hasBean("customRegistry"));
	}

	@Test
	void definesPropertiesBasedConnectionDetailsByDefault() {
		this.contextRunner.withUserConfiguration(BaseConfiguration.class)
			.run((context) -> assertThat(context).hasSingleBean(PropertiesOpenTSDBMetricsConnectionDetails.class));
	}

	@Test
	void testConnectionFactoryWithOverridesWhenUsingCustomConnectionDetails() {
		this.contextRunner.withUserConfiguration(BaseConfiguration.class, ConnectionDetailsConfiguration.class)
			.run((context) -> {
				assertThat(context).hasSingleBean(OpenTSDBMetricsConnectionDetails.class)
					.doesNotHaveBean(PropertiesOpenTSDBMetricsConnectionDetails.class);
				OpenTSDBConfig config = context.getBean(OpenTSDBConfig.class);
				assertThat(config.uri()).isEqualTo("http://localhost:12345/metrics");
			});
	}

	@Configuration(proxyBeanMethods = false)
	static class BaseConfiguration {

		@Bean
		Clock customClock() {
			return Clock.SYSTEM;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(BaseConfiguration.class)
	static class CustomConfigConfiguration {

		@Bean
		OpenTSDBConfig customConfig() {
			return (key) -> null;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Import(BaseConfiguration.class)
	static class CustomRegistryConfiguration {

		@Bean
		OpenTSDBMeterRegistry customRegistry(OpenTSDBConfig config, Clock clock) {
			return new OpenTSDBMeterRegistry(config, clock);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ConnectionDetailsConfiguration {

		@Bean
		OpenTSDBMetricsConnectionDetails openTSDBMetricsConnectionDetails() {
			return () -> "http://localhost:12345/metrics";
		}

	}

}
