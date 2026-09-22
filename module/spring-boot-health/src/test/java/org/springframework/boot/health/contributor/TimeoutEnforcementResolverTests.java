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

package org.springframework.boot.health.contributor;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TimeoutEnforcementResolver}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class TimeoutEnforcementResolverTests {

	private static final String WARNING = "doesn't override health(Duration)";

	private final TimeoutEnforcementResolver resolver = new TimeoutEnforcementResolver();

	@Test
	void shouldKeepFrameworkEnforcement(CapturedOutput output) {
		HealthIndicator indicator = Health.up()::build;
		assertThat(resolve(indicator)).isEqualTo(TimeoutEnforcement.FRAMEWORK);
		assertThat(output).doesNotContain(WARNING);
	}

	@Test
	void shouldKeepIndicatorEnforcementWhenHealthWithTimeoutIsOverridden(CapturedOutput output) {
		assertThat(resolve(new BoundingIndicator())).isEqualTo(TimeoutEnforcement.INDICATOR);
		assertThat(output).doesNotContain(WARNING);
	}

	@Test
	void shouldFallBackToFrameworkEnforcementWhenHealthWithTimeoutIsNotOverridden(CapturedOutput output) {
		assertThat(resolve(new MisdeclaringIndicator())).isEqualTo(TimeoutEnforcement.FRAMEWORK);
		assertThat(output).contains(MisdeclaringIndicator.class.getName()).contains(WARNING);
	}

	@Test
	void shouldReportMisdeclaredIndicatorOnce(CapturedOutput output) {
		resolve(new MisdeclaringIndicator());
		resolve(new MisdeclaringIndicator());
		assertThat(StringUtils.countOccurrencesOf(output.toString(), WARNING)).isOne();
	}

	@Test
	void shouldResolveAdaptedIndicatorThroughItsDelegate(CapturedOutput output) {
		ReactiveHealthIndicator adapted = (ReactiveHealthIndicator) ReactiveHealthContributor
			.adapt(new MisdeclaringIndicator());
		assertThat(resolve(adapted)).isEqualTo(TimeoutEnforcement.FRAMEWORK);
		assertThat(output).contains(MisdeclaringIndicator.class.getName()).contains(WARNING);
	}

	@Test
	void shouldResolveAdaptedReactiveIndicatorThroughItsDelegate(CapturedOutput output) {
		HealthIndicator adapted = new MisdeclaringReactiveIndicator().asHealthContributor();
		assertThat(resolve(adapted)).isEqualTo(TimeoutEnforcement.FRAMEWORK);
		assertThat(output).contains(MisdeclaringReactiveIndicator.class.getName()).contains(WARNING);
	}

	private TimeoutEnforcement resolve(HealthIndicator indicator) {
		return this.resolver.resolve(indicator.getTimeoutEnforcement(), indicator);
	}

	private TimeoutEnforcement resolve(ReactiveHealthIndicator indicator) {
		return this.resolver.resolve(indicator.getTimeoutEnforcement(), indicator);
	}

	/**
	 * An indicator which declares {@link TimeoutEnforcement#INDICATOR} and bounds its
	 * check.
	 */
	private static final class BoundingIndicator implements HealthIndicator {

		@Override
		public TimeoutEnforcement getTimeoutEnforcement() {
			return TimeoutEnforcement.INDICATOR;
		}

		@Override
		public Health health(Duration timeout) {
			return health();
		}

		@Override
		public Health health() {
			return Health.up().build();
		}

	}

	/**
	 * An indicator which declares {@link TimeoutEnforcement#INDICATOR} without bounding
	 * its check.
	 */
	private static final class MisdeclaringIndicator implements HealthIndicator {

		@Override
		public TimeoutEnforcement getTimeoutEnforcement() {
			return TimeoutEnforcement.INDICATOR;
		}

		@Override
		public Health health() {
			return Health.up().build();
		}

	}

	/**
	 * A reactive indicator which declares {@link TimeoutEnforcement#INDICATOR} without
	 * bounding its check.
	 */
	private static final class MisdeclaringReactiveIndicator implements ReactiveHealthIndicator {

		@Override
		public TimeoutEnforcement getTimeoutEnforcement() {
			return TimeoutEnforcement.INDICATOR;
		}

		@Override
		public Mono<Health> health() {
			return Mono.just(Health.up().build());
		}

	}

}
