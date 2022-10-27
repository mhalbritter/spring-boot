/*
 * Copyright 2012-2022 the original author or authors.
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

package org.springframework.boot.actuate.autoconfigure.tracing;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TracingDisabledAutoConfiguration}.
 *
 * @author Moritz Halbritter
 */
class TracingDisabledAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TracingDisabledAutoConfiguration.class));

	@Test
	void shouldSupplyBeans() {
		this.contextRunner.withPropertyValues("management.tracing.enabled=false")
				.run((context) -> assertThat(context).hasSingleBean(Tracer.class));
	}

	@Test
	void shouldNotSupplyBeansWhenTracingIsEnabled() {
		this.contextRunner.withPropertyValues("management.tracing.enabled=true")
				.run((context) -> assertThat(context).doesNotHaveBean(Tracer.class));
	}

	@Test
	void shouldNotSupplyBeansWhenTracingIsImplicitelyEnabled() {
		this.contextRunner.run((context) -> assertThat(context).doesNotHaveBean(Tracer.class));
	}

}
