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

/**
 * Declares how a {@link HealthIndicator} or {@link ReactiveHealthIndicator} applies a
 * configured execution timeout. See {@link HealthIndicator#getTimeoutSupport()} and
 * {@link ReactiveHealthIndicator#getTimeoutSupport()}.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
public enum TimeoutSupport {

	/**
	 * The indicator does not enforce a configured timeout; the check runs as without a
	 * limit. If a timeout is configured in the environment anyway, Spring Boot logs a
	 * one-time warning per indicator name.
	 */
	NONE,
	/**
	 * The indicator receives the configured {@link java.time.Duration} through
	 * {@link HealthIndicator#health(Duration)} or
	 * {@link ReactiveHealthIndicator#health(Duration)} and is responsible for honoring
	 * it.
	 */
	NATIVE,
	/**
	 * The framework enforces the limit externally: for blocking indicators the check runs
	 * on another thread and is cancelled with interruption after the timeout; for
	 * reactive indicators {@link reactor.core.publisher.Mono#timeout(java.time.Duration)}
	 * is used. This mode is best-effort and may not stop work immediately when underlying
	 * code does not respond to interruption or cancellation.
	 */
	INTERRUPTION

}
