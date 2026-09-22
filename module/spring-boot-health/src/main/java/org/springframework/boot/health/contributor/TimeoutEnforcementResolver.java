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

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogMessage;
import org.springframework.util.ReflectionUtils;

/**
 * Resolves the {@link TimeoutEnforcement} an indicator can actually deliver.
 * <p>
 * {@link TimeoutEnforcement#INDICATOR} stops Spring Boot from capping the check, so an
 * indicator which declares it without overriding {@code health(Duration)} would leave the
 * check unbounded. Such an indicator is reported once and capped by the framework
 * instead.
 *
 * @author Moritz Halbritter
 */
class TimeoutEnforcementResolver {

	private static final Log logger = LogFactory.getLog(TimeoutEnforcementResolver.class);

	private static final String HEALTH_METHOD = "health";

	private final Map<Class<?>, Boolean> overrides = new ConcurrentHashMap<>();

	private final Set<Class<?>> reported = ConcurrentHashMap.newKeySet();

	/**
	 * Resolves the enforcement to apply to an indicator.
	 * @param declared the enforcement the indicator declares
	 * @param indicator the indicator, an adapted one being resolved through its delegate
	 * @return the enforcement to apply
	 */
	TimeoutEnforcement resolve(TimeoutEnforcement declared, Object indicator) {
		if (declared != TimeoutEnforcement.INDICATOR) {
			return declared;
		}
		Object target = unwrap(indicator);
		if (overridesHealth(target.getClass())) {
			return TimeoutEnforcement.INDICATOR;
		}
		report(target.getClass());
		return TimeoutEnforcement.FRAMEWORK;
	}

	/**
	 * Returns whether an indicator has any use for a timeout. One which doesn't is called
	 * without it, so that an override of {@code health(boolean)} keeps being used rather
	 * than being bypassed by the default {@code health(Duration, boolean)}.
	 * @param indicator the indicator, an adapted one being resolved through its delegate
	 * @return whether to pass the timeout to the indicator
	 */
	boolean acceptsTimeout(Object indicator) {
		return overridesHealth(unwrap(indicator).getClass());
	}

	/**
	 * Returns the indicator which ends up running the check, looking through any number
	 * of blocking to reactive adaptions.
	 * @param indicator the indicator to unwrap
	 * @return the adapted indicator
	 */
	private Object unwrap(Object indicator) {
		Object candidate = indicator;
		while (true) {
			if (candidate instanceof HealthIndicatorAdapter adapter) {
				candidate = adapter.getDelegate();
				continue;
			}
			if (candidate instanceof ReactiveHealthIndicatorAdapter adapter) {
				candidate = adapter.getDelegate();
				continue;
			}
			return candidate;
		}
	}

	/**
	 * Returns whether an indicator overrides {@code health(Duration)}. An adapted
	 * indicator can implement either contract, so both defaults are looked for.
	 * @param indicatorType the type of the indicator
	 * @return whether the indicator overrides the method
	 */
	private boolean overridesHealth(Class<?> indicatorType) {
		return this.overrides.computeIfAbsent(indicatorType, (type) -> {
			Method method = ReflectionUtils.findMethod(type, HEALTH_METHOD, Duration.class);
			if (method == null) {
				return false;
			}
			Class<?> declaringClass = method.getDeclaringClass();
			return !declaringClass.equals(HealthIndicator.class)
					&& !declaringClass.equals(ReactiveHealthIndicator.class);
		});
	}

	private void report(Class<?> indicatorType) {
		if (!this.reported.add(indicatorType)) {
			return;
		}
		logger.warn(
				LogMessage.format(
						"'%s' declares TimeoutEnforcement.INDICATOR but doesn't override health(Duration), "
								+ "capping its health check with the framework timeout instead",
						indicatorType.getName()));
	}

}
