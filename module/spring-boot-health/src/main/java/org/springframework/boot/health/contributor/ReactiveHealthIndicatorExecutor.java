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
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.core.env.PropertyResolver;

/**
 * Allows to execute {@link ReactiveHealthIndicator ReactiveHealthIndicators} with a
 * timeout.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
public class ReactiveHealthIndicatorExecutor extends AbstractHealthIndicatorExecutor {

	private static final Log logger = LogFactory.getLog(ReactiveHealthIndicatorExecutor.class);

	public ReactiveHealthIndicatorExecutor(@Nullable PropertyResolver propertyResolver) {
		super(logger, propertyResolver);
	}

	/**
	 * Executes a {@link ReactiveHealthIndicator} with a timeout if necessary. If a
	 * timeout occurs, {@link Health#down()} with reason 'timeout' is returned.
	 * @param reactiveHealthIndicator the indicator to execute
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return the health
	 */
	public Mono<Health> execute(ReactiveHealthIndicator reactiveHealthIndicator, String indicatorName,
			boolean includeDetails) {
		Duration timeout = getTimeout(indicatorName);
		if (timeout == null) {
			return reactiveHealthIndicator.health(includeDetails);
		}
		Mono<Health> health = switch (reactiveHealthIndicator.getTimeoutSupport()) {
			case NONE -> {
				logTimeoutNotSupportedWarningOnce(indicatorName, timeout);
				yield reactiveHealthIndicator.health(includeDetails);
			}
			case NATIVE -> reactiveHealthIndicator.health(timeout, includeDetails);
			case INTERRUPTION -> reactiveHealthIndicator.health(includeDetails).timeout(timeout);
		};
		return health.onErrorResume(TimeoutException.class,
				(ex) -> Mono.just(Health.down(ex).withDetail("reason", "timeout").build()));
	}

}
