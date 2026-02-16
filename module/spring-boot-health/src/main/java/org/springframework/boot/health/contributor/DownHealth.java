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

import org.apache.commons.logging.Log;

import org.springframework.core.log.LogMessage;

/**
 * Builds the {@link Health#down()} an executor reports in place of an indicator which
 * could not answer.
 *
 * @author Moritz Halbritter
 */
final class DownHealth {

	private static final String REASON_DETAIL = "reason";

	private DownHealth() {
	}

	/**
	 * Reports a failure of an indicator as {@link Health#down()}. The reason and the
	 * exception are details, so they are dropped unless the caller asked for details.
	 * @param ex the failure
	 * @param reason distinguishes the kind of failure
	 * @param includeDetails whether to include details
	 * @return the health
	 */
	static Health of(Throwable ex, DownReason reason, boolean includeDetails) {
		Health health = Health.down(ex).withDetail(REASON_DETAIL, reason.value()).build();
		return includeDetails ? health : health.withoutDetails();
	}

	/**
	 * Reports an indicator which failed with an unexpected exception as
	 * {@link Health#down()}, and logs the failure at warn level.
	 * @param logger the logger of the executor which reports the failure
	 * @param ex the failure
	 * @param reason distinguishes the kind of failure
	 * @param indicatorName the name of the indicator
	 * @param includeDetails whether to include details
	 * @return the health
	 */
	static Health logged(Log logger, Throwable ex, DownReason reason, String indicatorName, boolean includeDetails) {
		logger.warn(LogMessage.format("Health indicator %s failed", indicatorName), ex);
		return of(ex, reason, includeDetails);
	}

}
