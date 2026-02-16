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
 * Where {@link HealthIndicatorExecutor} runs a check which it puts no deadline on.
 *
 * @author Moritz Halbritter
 */
enum ThreadingMode {

	/**
	 * Run the check on the thread which asked for the health. Used by the servlet path,
	 * where that thread is a request thread which is allowed to block.
	 */
	CALLING_THREAD,

	/**
	 * Run the check on the executor's pool, which caps how many threads an indicator can
	 * occupy. Used by the reactive bridge, where the calling thread is an event loop.
	 */
	POOL

}
