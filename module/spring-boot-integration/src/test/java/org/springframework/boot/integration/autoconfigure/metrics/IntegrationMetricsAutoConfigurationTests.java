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

package org.springframework.boot.integration.autoconfigure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration;
import org.springframework.boot.integration.autoconfigure.endpoint.IntegrationGraphEndpointAutoConfiguration;
import org.springframework.boot.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IntegrationMetricsAutoConfiguration}.
 *
 * @author Artem Bilan
 */
class IntegrationMetricsAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(IntegrationAutoConfiguration.class,
				IntegrationGraphEndpointAutoConfiguration.class, IntegrationMetricsAutoConfiguration.class,
				MetricsAutoConfiguration.class))
		.withBean(SimpleMeterRegistry.class)
		.withPropertyValues("management.metrics.tags.someTag=someValue",
				"management.metrics.use-global-registry=false");

	@Test
	void integrationMetersAreInstrumented() {
		this.contextRunner.run((context) -> {
			MeterRegistry registry = context.getBean(MeterRegistry.class);
			Gauge gauge = registry.get("spring.integration.channels").tag("someTag", "someValue").gauge();
			assertThat(gauge).isNotNull().extracting(Gauge::value).isEqualTo(2.0);
		});
	}

}
