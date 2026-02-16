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
 * Why an executor reports an indicator as {@link Health#down()} instead of the health the
 * indicator itself would report. The value is exposed as the {@code reason} detail of
 * that health, so it is part of what the health endpoint documents.
 *
 * @author Moritz Halbritter
 */
enum DownReason {

	/**
	 * The check did not answer within its timeout.
	 */
	TIMEOUT("timeout"),

	/**
	 * The indicator has as many checks in flight as it is allowed to have.
	 */
	CONCURRENCY_LIMIT("concurrency-limit"),

	/**
	 * The timeout configured for the indicator cannot be used.
	 */
	INVALID_TIMEOUT("invalid-timeout"),

	/**
	 * The executor refused to run the check.
	 */
	REJECTED("rejected"),

	/**
	 * The check was requested after the executor had been destroyed.
	 */
	DISPOSED("disposed"),

	/**
	 * The check failed with an unexpected exception, or signaled an error.
	 */
	EXECUTION_FAILED("execution-failed");

	private final String value;

	DownReason(String value) {
		this.value = value;
	}

	String value() {
		return this.value;
	}

}
