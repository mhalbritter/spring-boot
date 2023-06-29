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

package org.springframework.boot.actuate.autoconfigure.observation.web.reactive;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.test.MetricsRun;
import org.springframework.boot.actuate.autoconfigure.metrics.web.TestController;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebHttpHandlerBuilderCustomizer;
import org.springframework.boot.test.context.assertj.AssertableReactiveWebApplicationContext;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WebFluxObservationAutoConfiguration}
 *
 * @author Brian Clozel
 * @author Dmytro Nosan
 * @author Madhura Bhave
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class WebFluxObservationAutoConfigurationTests {

	private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
		.with(MetricsRun.simple())
		.withConfiguration(
				AutoConfigurations.of(ObservationAutoConfiguration.class, WebFluxObservationAutoConfiguration.class));

	@Test
	void shouldConfigureObservationRegistryOnHttpHandler() {
		this.contextRunner.run((context) -> {
			assertThat(context).hasSingleBean(ObservationRegistry.class);
			ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
			WebHttpHandlerBuilder builder = runCustomizer(context);
			assertThat(builder).extracting("observationRegistry").isEqualTo(observationRegistry);
		});
	}

	@Test
	void shouldConfigureDefaultConventionOnHttpHandler() {
		this.contextRunner.run((context) -> {
			WebHttpHandlerBuilder builder = runCustomizer(context);
			assertThat(builder).extracting("observationConvention")
				.isInstanceOf(DefaultServerRequestObservationConvention.class);
		});
	}

	@Test
	void shouldConfigureCustomConventionOnHttpHandlerWhenAvailable() {
		this.contextRunner.withUserConfiguration(CustomConventionConfiguration.class).run((context) -> {
			WebHttpHandlerBuilder builder = runCustomizer(context);
			assertThat(builder).extracting("observationConvention").isInstanceOf(CustomConvention.class);
		});
	}

	@Test
	void afterMaxUrisReachedFurtherUrisAreDenied(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TestController.class)
			.withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class, ObservationAutoConfiguration.class,
					WebFluxAutoConfiguration.class))
			.withPropertyValues("management.metrics.web.server.max-uri-tags=2")
			.run((context) -> {
				MeterRegistry registry = getInitializedMeterRegistry(context);
				assertThat(registry.get("http.server.requests").meters()).hasSizeLessThanOrEqualTo(2);
				assertThat(output).contains("Reached the maximum number of URI tags for 'http.server.requests'");
			});
	}

	@Test
	void afterMaxUrisReachedFurtherUrisAreDeniedWhenUsingCustomObservationName(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TestController.class)
			.withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class, ObservationAutoConfiguration.class,
					WebFluxAutoConfiguration.class))
			.withPropertyValues("management.metrics.web.server.max-uri-tags=2",
					"management.observations.http.server.requests.name=my.http.server.requests")
			.run((context) -> {
				MeterRegistry registry = getInitializedMeterRegistry(context);
				assertThat(registry.get("my.http.server.requests").meters()).hasSizeLessThanOrEqualTo(2);
				assertThat(output).contains("Reached the maximum number of URI tags for 'my.http.server.requests'");
			});
	}

	@Test
	void shouldNotDenyNorLogIfMaxUrisIsNotReached(CapturedOutput output) {
		this.contextRunner.withUserConfiguration(TestController.class)
			.withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class, ObservationAutoConfiguration.class,
					WebFluxAutoConfiguration.class))
			.withPropertyValues("management.metrics.web.server.max-uri-tags=5")
			.run((context) -> {
				MeterRegistry registry = getInitializedMeterRegistry(context);
				assertThat(registry.get("http.server.requests").meters()).hasSize(3);
				assertThat(output).doesNotContain("Reached the maximum number of URI tags for 'http.server.requests'");
			});
	}

	private MeterRegistry getInitializedMeterRegistry(AssertableReactiveWebApplicationContext context) {
		return getInitializedMeterRegistry(context, "/test0", "/test1", "/test2");
	}

	private MeterRegistry getInitializedMeterRegistry(AssertableReactiveWebApplicationContext context, String... urls) {
		WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
		for (String url : urls) {
			client.get().uri(url).exchange().expectStatus().isOk();
		}
		return context.getBean(MeterRegistry.class);
	}

	private static WebHttpHandlerBuilder runCustomizer(AssertableReactiveWebApplicationContext context) {
		assertThat(context).hasSingleBean(WebHttpHandlerBuilderCustomizer.class);
		WebHttpHandlerBuilderCustomizer customizer = context.getBean(WebHttpHandlerBuilderCustomizer.class);
		WebHttpHandlerBuilder builder = WebHttpHandlerBuilder.webHandler(mock(WebHandler.class));
		customizer.customize(builder);
		return builder;
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomConventionConfiguration {

		@Bean
		CustomConvention customConvention() {
			return new CustomConvention();
		}

	}

	static class CustomConvention extends DefaultServerRequestObservationConvention {

	}

}
