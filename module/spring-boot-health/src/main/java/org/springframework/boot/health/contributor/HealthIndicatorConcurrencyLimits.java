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

import org.springframework.boot.health.contributor.InFlightExecutions.Key;

/**
 * How many executions of the same health indicator may be in flight at once, for callers
 * which see details and for those which do not.
 *
 * @param detailed the limit for callers which see details
 * @param summary the limit for callers which do not see details
 * @author Moritz Halbritter
 */
record HealthIndicatorConcurrencyLimits(int detailed, int summary) {

	static final int DEFAULT_LIMIT = 4;

	static final HealthIndicatorConcurrencyLimits DEFAULTS = new HealthIndicatorConcurrencyLimits(DEFAULT_LIMIT,
			DEFAULT_LIMIT);

	/**
	 * Returns the limit which applies to the given check.
	 * @param key the key of the check
	 * @return the limit
	 */
	int get(Key key) {
		return (key.includeDetails()) ? this.detailed : this.summary;
	}

}
