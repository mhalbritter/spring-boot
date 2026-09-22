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

package org.springframework.boot.actuate.health;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.ApiVersion;
import org.springframework.boot.actuate.endpoint.web.test.WebEndpointTest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointWebExtension;
import org.springframework.boot.health.actuate.endpoint.ReactiveHealthEndpointWebExtension;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorNameGenerator;
import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.AbstractTimeoutAwareReactiveHealthIndicator;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicatorExecutor;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.ReactiveHealthIndicatorExecutor;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.boot.health.registry.DefaultHealthContributorRegistry;
import org.springframework.boot.health.registry.DefaultReactiveHealthContributorRegistry;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link HealthEndpoint} and {@link HealthEndpointWebExtension}
 * exposed by Jersey, Spring MVC, and WebFlux.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 */
class HealthEndpointWebIntegrationTests {

	private static final String V2_JSON = ApiVersion.V2.getProducedMimeType().toString();

	private static final String V3_JSON = ApiVersion.V3.getProducedMimeType().toString();

	@WebEndpointTest
	void whenHealthIsUp200ResponseIsReturned(WebTestClient client) {
		client.get()
			.uri("/actuator/health")
			.accept(MediaType.APPLICATION_JSON)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("status")
			.isEqualTo("UP")
			.jsonPath("components.alpha.status")
			.isEqualTo("UP")
			.jsonPath("components.bravo.status")
			.isEqualTo("UP");
	}

	@WebEndpointTest
	void whenHealthIsUpAndAcceptsV3Request200ResponseIsReturned(WebTestClient client) {
		client.get()
			.uri("/actuator/health")
			.headers((headers) -> headers.set(HttpHeaders.ACCEPT, V3_JSON))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("status")
			.isEqualTo("UP")
			.jsonPath("components.alpha.status")
			.isEqualTo("UP")
			.jsonPath("components.bravo.status")
			.isEqualTo("UP");
	}

	@WebEndpointTest
	void whenHealthIsUpAndAcceptsAllRequest200ResponseIsReturned(WebTestClient client) {
		client.get()
			.uri("/actuator/health")
			.headers((headers) -> headers.set(HttpHeaders.ACCEPT, "*/*"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("status")
			.isEqualTo("UP")
			.jsonPath("components.alpha.status")
			.isEqualTo("UP")
			.jsonPath("components.bravo.status")
			.isEqualTo("UP");
	}

	@WebEndpointTest
	void whenHealthIsUpAndV2Request200ResponseIsReturnedInV2Format(WebTestClient client) {
		client.get()
			.uri("/actuator/health")
			.headers((headers) -> headers.set(HttpHeaders.ACCEPT, V2_JSON))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("status")
			.isEqualTo("UP")
			.jsonPath("details.alpha.status")
			.isEqualTo("UP")
			.jsonPath("details.bravo.status")
			.isEqualTo("UP");
	}

	@WebEndpointTest
	void whenHealthIsDown503ResponseIsReturned(ApplicationContext context, WebTestClient client) {
		HealthIndicator healthIndicator = () -> Health.down().build();
		ReactiveHealthIndicator reactiveHealthIndicator = () -> Mono.just(Health.down().build());
		withHealthContributor(context, "charlie", healthIndicator, reactiveHealthIndicator,
				() -> client.get()
					.uri("/actuator/health")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus()
					.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
					.expectBody()
					.jsonPath("status")
					.isEqualTo("DOWN")
					.jsonPath("components.alpha.status")
					.isEqualTo("UP")
					.jsonPath("components.bravo.status")
					.isEqualTo("UP")
					.jsonPath("components.charlie.status")
					.isEqualTo("DOWN"));
	}

	@WebEndpointTest
	void whenComponentHealthIsDown503ResponseIsReturned(ApplicationContext context, WebTestClient client) {
		HealthIndicator healthIndicator = () -> Health.down().build();
		ReactiveHealthIndicator reactiveHealthIndicator = () -> Mono.just(Health.down().build());
		withHealthContributor(context, "charlie", healthIndicator, reactiveHealthIndicator,
				() -> client.get()
					.uri("/actuator/health/charlie")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus()
					.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
					.expectBody()
					.jsonPath("status")
					.isEqualTo("DOWN"));
	}

	@WebEndpointTest
	void whenComponentInstanceHealthIsDown503ResponseIsReturned(ApplicationContext context, WebTestClient client) {
		HealthIndicator healthIndicator = () -> Health.down().build();
		CompositeHealthContributor composite = CompositeHealthContributor
			.fromMap(Collections.singletonMap("one", healthIndicator));
		ReactiveHealthIndicator reactiveHealthIndicator = () -> Mono.just(Health.down().build());
		CompositeReactiveHealthContributor reactiveComposite = CompositeReactiveHealthContributor
			.fromMap(Collections.singletonMap("one", reactiveHealthIndicator));
		withHealthContributor(context, "charlie", composite, reactiveComposite,
				() -> client.get()
					.uri("/actuator/health/charlie/one")
					.accept(MediaType.APPLICATION_JSON)
					.exchange()
					.expectStatus()
					.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
					.expectBody()
					.jsonPath("status")
					.isEqualTo("DOWN"));
	}

	@WebEndpointTest
	void whenHealthIndicatorExceedsTimeoutReturns503(ApplicationContext context, WebTestClient client) {
		HealthIndicator slowIndicator = () -> {
			sleep(Duration.ofSeconds(2));
			return Health.up().build();
		};
		ReactiveHealthIndicator reactiveSlowIndicator = () -> Mono.just(Health.up().build())
			.delayElement(Duration.ofSeconds(2));
		withTimeout(context, "charlie", Duration.ofMillis(50),
				() -> withHealthContributor(context, "charlie", slowIndicator, reactiveSlowIndicator,
						() -> client.get()
							.uri("/actuator/health")
							.accept(MediaType.APPLICATION_JSON)
							.exchange()
							.expectStatus()
							.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
							.expectBody()
							.jsonPath("status")
							.isEqualTo("DOWN")
							.jsonPath("components.charlie.status")
							.isEqualTo("DOWN")
							.jsonPath("components.charlie.details.reason")
							.isEqualTo("timeout")));
	}

	@WebEndpointTest
	void whenHealthIndicatorExceedsTimeoutAndDetailsAreHiddenNoReasonIsExposed(ApplicationContext context,
			WebTestClient client) {
		HealthIndicator slowIndicator = () -> {
			sleep(Duration.ofSeconds(2));
			return Health.up().build();
		};
		ReactiveHealthIndicator reactiveSlowIndicator = () -> Mono.just(Health.up().build())
			.delayElement(Duration.ofSeconds(2));
		withTimeout(context, "charlie", Duration.ofMillis(50),
				() -> withHiddenDetails(context,
						() -> withHealthContributor(context, "charlie", slowIndicator, reactiveSlowIndicator,
								() -> client.get()
									.uri("/actuator/health")
									.accept(MediaType.APPLICATION_JSON)
									.exchange()
									.expectStatus()
									.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
									.expectBody()
									.jsonPath("status")
									.isEqualTo("DOWN")
									.jsonPath("components.charlie.status")
									.isEqualTo("DOWN")
									.jsonPath("components.charlie.details")
									.doesNotExist())));
	}

	@WebEndpointTest
	void whenHealthIndicatorEnforcesTimeoutItIsGivenTheConfiguredDuration(ApplicationContext context,
			WebTestClient client) {
		Duration timeout = Duration.ofSeconds(10);
		AtomicReference<@Nullable Duration> enforced = new AtomicReference<>();
		HealthIndicator selfEnforcing = new AbstractTimeoutAwareHealthIndicator(TimeoutEnforcement.INDICATOR) {
			@Override
			protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
				enforced.set(timeout);
				builder.up();
			}
		};
		ReactiveHealthIndicator reactiveSelfEnforcing = new AbstractTimeoutAwareReactiveHealthIndicator(
				TimeoutEnforcement.INDICATOR) {
			@Override
			protected Mono<Health> doHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
				enforced.set(timeout);
				return Mono.just(builder.up().build());
			}
		};
		withTimeout(context, "charlie", timeout,
				() -> withHealthContributor(context, "charlie", selfEnforcing, reactiveSelfEnforcing,
						() -> client.get()
							.uri("/actuator/health")
							.accept(MediaType.APPLICATION_JSON)
							.exchange()
							.expectStatus()
							.isOk()
							.expectBody()
							.jsonPath("components.charlie.status")
							.isEqualTo("UP")));
		assertThat(enforced).hasValue(timeout);
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(ex);
		}
	}

	private void withTimeout(ApplicationContext context, String indicatorName, Duration timeout,
			ThrowingCallable callable) {
		ConfigurableEnvironment environment = (ConfigurableEnvironment) context.getEnvironment();
		MockPropertySource propertySource = new MockPropertySource("timeout-test");
		propertySource.setProperty("management.health.%s.timeout".formatted(indicatorName), timeout);
		environment.getPropertySources().addFirst(propertySource);
		try {
			callable.call();
		}
		catch (Throwable ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		finally {
			environment.getPropertySources().remove("timeout-test");
		}
	}

	// A caller which is not authorized to see details must learn no more than DOWN, so
	// the
	// reason of a timeout is a detail like any other.
	private void withHiddenDetails(ApplicationContext context, ThrowingCallable callable) {
		TestHealthEndpointGroup primary = (TestHealthEndpointGroup) context.getBean(HealthEndpointGroups.class)
			.getPrimary();
		primary.setShowComponents(true);
		primary.setShowDetails(false);
		try {
			callable.call();
		}
		catch (Throwable ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		finally {
			primary.setShowComponents(null);
			primary.setShowDetails(true);
		}
	}

	private void withHealthContributor(ApplicationContext context, String name, HealthContributor healthContributor,
			ReactiveHealthContributor reactiveHealthContributor, ThrowingCallable callable) {
		HealthContributorRegistry healthContributorRegistry = getContributorRegistry(context,
				HealthContributorRegistry.class);
		healthContributorRegistry.registerContributor(name, healthContributor);
		ReactiveHealthContributorRegistry reactiveHealthContributorRegistry = getContributorRegistry(context,
				ReactiveHealthContributorRegistry.class);
		if (reactiveHealthContributorRegistry != null) {
			reactiveHealthContributorRegistry.registerContributor(name, reactiveHealthContributor);
		}
		try {
			callable.call();
		}
		catch (Throwable ex) {
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		finally {
			healthContributorRegistry.unregisterContributor(name);
			if (reactiveHealthContributorRegistry != null) {
				reactiveHealthContributorRegistry.unregisterContributor(name);
			}
		}
	}

	private <R> R getContributorRegistry(ApplicationContext context, Class<R> registryType) {
		return context.getBeanProvider(registryType).getIfAvailable();
	}

	@WebEndpointTest
	void whenHealthIndicatorIsRemovedResponseIsAltered(WebTestClient client, ApplicationContext context) {
		String name = "bravo";
		HealthContributorRegistry healthContributorRegistry = getContributorRegistry(context,
				HealthContributorRegistry.class);
		HealthContributor bravo = healthContributorRegistry.unregisterContributor(name);
		ReactiveHealthContributorRegistry reactiveHealthContributorRegistry = getContributorRegistry(context,
				ReactiveHealthContributorRegistry.class);
		ReactiveHealthContributor reactiveBravo = (reactiveHealthContributorRegistry != null)
				? reactiveHealthContributorRegistry.unregisterContributor(name) : null;
		try {
			client.get()
				.uri("/actuator/health")
				.accept(MediaType.APPLICATION_JSON)
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("status")
				.isEqualTo("UP")
				.jsonPath("components.alpha.status")
				.isEqualTo("UP")
				.jsonPath("components.bravo.status")
				.doesNotExist();
		}
		finally {
			healthContributorRegistry.registerContributor(name, bravo);
			if (reactiveHealthContributorRegistry != null && reactiveBravo != null) {
				reactiveHealthContributorRegistry.registerContributor(name, reactiveBravo);
			}
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfiguration {

		@Bean
		HealthContributorRegistry healthContributorRegistry(Map<String, HealthContributor> contributorBeans) {
			return new DefaultHealthContributorRegistry(null,
					HealthContributorNameGenerator.withoutStandardSuffixes().registrar(contributorBeans));
		}

		@Bean
		@ConditionalOnWebApplication(type = Type.REACTIVE)
		ReactiveHealthContributorRegistry reactiveHealthContributorRegistry(
				Map<String, ReactiveHealthContributor> contributorBeans) {
			return new DefaultReactiveHealthContributorRegistry(null,
					HealthContributorNameGenerator.withoutStandardSuffixes().registrar(contributorBeans));
		}

		@Bean
		HealthIndicatorExecutor healthIndicatorExecutor(Environment environment) {
			return new HealthIndicatorExecutor(environment);
		}

		@Bean
		@ConditionalOnWebApplication(type = Type.REACTIVE)
		ReactiveHealthIndicatorExecutor reactiveHealthIndicatorExecutor(
				HealthIndicatorExecutor healthIndicatorExecutor) {
			return new ReactiveHealthIndicatorExecutor(healthIndicatorExecutor);
		}

		@Bean
		HealthEndpoint healthEndpoint(HealthContributorRegistry healthContributorRegistry,
				ObjectProvider<ReactiveHealthContributorRegistry> reactiveHealthContributorRegistry,
				HealthEndpointGroups healthEndpointGroups, HealthIndicatorExecutor healthIndicatorExecutor) {
			return new HealthEndpoint(healthContributorRegistry, reactiveHealthContributorRegistry.getIfAvailable(),
					healthEndpointGroups, null, healthIndicatorExecutor);
		}

		@Bean
		@ConditionalOnWebApplication(type = Type.SERVLET)
		HealthEndpointWebExtension healthWebEndpointExtension(HealthContributorRegistry healthContributorRegistry,
				ObjectProvider<ReactiveHealthContributorRegistry> reactiveHealthContributorRegistry,
				HealthEndpointGroups healthEndpointGroups, HealthIndicatorExecutor healthIndicatorExecutor) {
			return new HealthEndpointWebExtension(healthContributorRegistry,
					reactiveHealthContributorRegistry.getIfAvailable(), healthEndpointGroups, null,
					healthIndicatorExecutor);
		}

		@Bean
		@ConditionalOnWebApplication(type = Type.REACTIVE)
		ReactiveHealthEndpointWebExtension reactiveHealthWebEndpointExtension(
				ReactiveHealthContributorRegistry reactiveHealthContributorRegistry,
				ObjectProvider<HealthContributorRegistry> healthContributorRegistry,
				HealthEndpointGroups healthEndpointGroups,
				ReactiveHealthIndicatorExecutor reactiveHealthIndicatorExecutor) {
			return new ReactiveHealthEndpointWebExtension(reactiveHealthContributorRegistry,
					healthContributorRegistry.getIfAvailable(), healthEndpointGroups, null,
					reactiveHealthIndicatorExecutor);
		}

		@Bean
		HealthEndpointGroups healthEndpointGroups() {
			TestHealthEndpointGroup primary = new TestHealthEndpointGroup();
			TestHealthEndpointGroup allTheAs = new TestHealthEndpointGroup((name) -> name.startsWith("a"));
			return HealthEndpointGroups.of(primary, Collections.singletonMap("alltheas", allTheAs));
		}

		@Bean
		HealthIndicator alphaHealthIndicator() {
			return () -> Health.up().build();
		}

		@Bean
		HealthIndicator bravoHealthIndicator() {
			return () -> Health.up().build();
		}

	}

}
