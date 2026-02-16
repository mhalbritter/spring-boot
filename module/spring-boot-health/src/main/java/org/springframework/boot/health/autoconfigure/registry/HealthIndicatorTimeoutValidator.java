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

package org.springframework.boot.health.autoconfigure.registry;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicatorTimeouts;
import org.springframework.boot.health.contributor.ReactiveHealthContributors;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.health.registry.ReactiveHealthContributorRegistry;
import org.springframework.core.env.Environment;

/**
 * Resolves the timeout of every registered health indicator once, at startup, so that a
 * misconfigured value is reported while the application is starting instead of on the
 * first request to the health endpoint.
 * <p>
 * Only names which are registered can be checked. A timeout configured under a name which
 * matches no indicator is never read, so it cannot be validated here and stays without
 * effect.
 *
 * @author Moritz Halbritter
 */
class HealthIndicatorTimeoutValidator implements InitializingBean {

	private static final String PATH_SEPARATOR = "/";

	private final HealthIndicatorTimeouts timeouts;

	private final HealthContributorRegistry registry;

	private final @Nullable ReactiveHealthContributorRegistry reactiveRegistry;

	HealthIndicatorTimeoutValidator(Environment environment, HealthContributorRegistry registry,
			@Nullable ReactiveHealthContributorRegistry reactiveRegistry) {
		this.timeouts = new HealthIndicatorTimeouts(environment);
		this.registry = registry;
		this.reactiveRegistry = reactiveRegistry;
	}

	@Override
	public void afterPropertiesSet() {
		validate(this.registry, "");
		if (this.reactiveRegistry != null) {
			validate(this.reactiveRegistry, "");
		}
	}

	private void validate(HealthContributors contributors, String prefix) {
		for (HealthContributors.Entry entry : contributors) {
			String name = prefix + entry.name();
			if (entry.contributor() instanceof CompositeHealthContributor composite) {
				validate(composite, name + PATH_SEPARATOR);
				continue;
			}
			validate(name);
		}
	}

	private void validate(ReactiveHealthContributors contributors, String prefix) {
		for (ReactiveHealthContributors.Entry entry : contributors) {
			String name = prefix + entry.name();
			if (entry.contributor() instanceof CompositeReactiveHealthContributor composite) {
				validate(composite, name + PATH_SEPARATOR);
				continue;
			}
			validate(name);
		}
	}

	private void validate(String indicatorName) {
		try {
			this.timeouts.get(indicatorName);
		}
		catch (RuntimeException ex) {
			// The property resolver reports the value and the target type, but not which
			// indicator asked for it.
			throw new IllegalStateException(
					"Invalid timeout configured for health indicator '%s'".formatted(indicatorName), ex);
		}
	}

}
