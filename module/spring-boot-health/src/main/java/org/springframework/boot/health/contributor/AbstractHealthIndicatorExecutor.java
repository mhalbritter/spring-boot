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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

import org.springframework.core.env.PropertyResolver;
import org.springframework.core.log.LogMessage;

/**
 * Abstract base class for executing health indicators with a timeout.
 *
 * @author Moritz Halbritter
 */
class AbstractHealthIndicatorExecutor {

	private static final String TIMEOUT_PROPERTY_TEMPLATE = "management.health.%s.timeout";

	private static final String DEFAULT_TIMEOUT_PROPERTY = "management.health.defaults.timeout";

	private final Log logger;

	private final Set<String> loggedIndicators = ConcurrentHashMap.newKeySet();

	private final @Nullable PropertyResolver propertyResolver;

	AbstractHealthIndicatorExecutor(Log logger, @Nullable PropertyResolver propertyResolver) {
		this.logger = logger;
		this.propertyResolver = propertyResolver;
	}

	protected @Nullable Duration getTimeout(String indicatorName) {
		if (this.propertyResolver == null) {
			return null;
		}
		Duration value = this.propertyResolver.getProperty(
				TIMEOUT_PROPERTY_TEMPLATE.formatted(indicatorName.toLowerCase(Locale.ROOT).replace('/', '.')),
				Duration.class);
		return (value != null) ? value : this.propertyResolver.getProperty(DEFAULT_TIMEOUT_PROPERTY, Duration.class);
	}

	protected void logTimeoutNotSupportedWarningOnce(String indicatorName, Duration timeout) {
		if (!this.logger.isWarnEnabled()) {
			return;
		}
		if (this.loggedIndicators.add(indicatorName)) {
			this.logger.warn(LogMessage.format(
					"Health indicator %s doesn't support timeout, but a timeout of %s has been configured",
					indicatorName, timeout));
		}
	}

}
