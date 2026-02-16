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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.ConversionException;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.Assert;

/**
 * The execution timeouts configured for health indicators, read from
 * {@code management.health.<indicator>.timeout} with
 * {@code management.health.defaults.timeout} as the fallback.
 * <p>
 * An indicator is named by its path in the contributor tree, so a leaf of a composite is
 * configured under the path of that leaf, with {@code /} written as {@code .}:
 * {@code management.health.mycomposite.myleaf.timeout}. A leaf without a timeout of its
 * own uses the timeout of its closest composite.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public class HealthIndicatorTimeouts {

	private static final String TIMEOUT_PROPERTY_TEMPLATE = "management.health.%s.timeout";

	private static final String DEFAULT_TIMEOUT_PROPERTY = "management.health.defaults.timeout";

	private final PropertyResolver propertyResolver;

	private final Map<String, Optional<Duration>> resolved = new ConcurrentHashMap<>();

	/**
	 * Creates a new instance.
	 * @param propertyResolver the property resolver to read the timeouts from
	 */
	public HealthIndicatorTimeouts(PropertyResolver propertyResolver) {
		Assert.notNull(propertyResolver, "'propertyResolver' must not be null");
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Returns the timeout configured for an indicator.
	 * @param indicatorName the name of the indicator
	 * @return the timeout, or {@code null} if the indicator has none
	 * @throws InvalidTimeoutException if the configured timeout cannot be read or is not
	 * positive
	 */
	public @Nullable Duration get(String indicatorName) {
		return this.resolved.computeIfAbsent(indicatorName, this::resolve).orElse(null);
	}

	private Optional<Duration> resolve(String indicatorName) {
		return Optional.ofNullable(findTimeout(indicatorName.toLowerCase(Locale.ROOT)));
	}

	private @Nullable Duration findTimeout(String name) {
		Duration timeout = getTimeout(TIMEOUT_PROPERTY_TEMPLATE.formatted(name.replace('/', '.')));
		if (timeout != null) {
			return timeout;
		}
		if (name.contains("/")) {
			String parent = name.substring(0, name.lastIndexOf("/"));
			return findTimeout(parent);
		}
		return getTimeout(DEFAULT_TIMEOUT_PROPERTY);
	}

	private @Nullable Duration getTimeout(String property) {
		Duration timeout = getProperty(property);
		if (timeout != null && timeout.compareTo(Duration.ZERO) <= 0) {
			throw new InvalidTimeoutException(property, timeout);
		}
		return timeout;
	}

	private @Nullable Duration getProperty(String property) {
		try {
			return this.propertyResolver.getProperty(property, Duration.class);
		}
		catch (ConversionException ex) {
			throw new InvalidTimeoutException(property, ex);
		}
	}

	/**
	 * Thrown when the timeout configured for a health indicator cannot be used.
	 */
	public static final class InvalidTimeoutException extends IllegalStateException {

		private InvalidTimeoutException(String property, Duration value) {
			super("Timeout configured in property '%s' must be positive, but was '%s'".formatted(property, value));
		}

		private InvalidTimeoutException(String property, Throwable cause) {
			super("Timeout configured in property '%s' cannot be read".formatted(property), cause);
		}

	}

}
