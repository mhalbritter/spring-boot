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

/**
 * Declares who caps a configured execution timeout for a
 * {@link TimeoutAwareHealthIndicator} or {@link TimeoutAwareReactiveHealthIndicator}, and
 * therefore where the check runs.
 *
 * @author Moritz Halbritter
 * @since 4.2.0
 */
public enum TimeoutEnforcement {

	/**
	 * The indicator itself caps the whole check and relies on the duration passed to
	 * {@link TimeoutAwareHealthIndicator#health(java.time.Duration)} to honor the limit.
	 * Such a check runs on the thread which asked for the health.
	 */
	INDICATOR,

	/**
	 * Spring Boot caps the check. The configured duration is still passed to
	 * {@link TimeoutAwareHealthIndicator#health(java.time.Duration)}, letting an
	 * indicator bound only part of its check. An indicator which does not implement
	 * {@link TimeoutAwareHealthIndicator} or {@link TimeoutAwareReactiveHealthIndicator}
	 * is always capped this way. A blocking check runs on another thread and is
	 * interrupted ({@link java.util.concurrent.Future#cancel(boolean)}) once the limit
	 * expires; a reactive check is bounded with
	 * {@link reactor.core.publisher.Mono#timeout}, which cancels the subscription.
	 * <p>
	 * Both bound the result, not the work: a call which ignores interruption or
	 * cancellation, for example one blocked on a hung network file system, keeps its
	 * thread until it returns.
	 * <p>
	 * Blocking checks run on threads of their own, capped per indicator, in a reactive
	 * application as well.
	 * <p>
	 * Concurrent probes of the same indicator share a single check, so a reported result
	 * may stem from a check which another caller started up to one timeout earlier.
	 * <p>
	 * A check must not depend on who asked for the health, and carries nothing of its
	 * caller: a blocking check runs on a thread of its own, a shared reactive check with
	 * an empty {@link reactor.util.context.Context}. The requested amount of detail is
	 * the exception, a check is started per {@code includeDetails} value.
	 */
	FRAMEWORK

}
