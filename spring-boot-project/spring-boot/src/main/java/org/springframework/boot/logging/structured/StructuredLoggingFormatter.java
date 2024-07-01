/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.boot.logging.structured;

/**
 * Formats a log event to a structured log message.
 * <p>
 * Implementing classes can declare the following parameter types in the constructor:
 * <p>
 * - {@link ApplicationMetadata}
 *
 * @param <E> the log event type
 * @author Moritz Halbritter
 * @since 3.4.0
 */
// TODO MH: Specialize for Logback and Log4j2? That would give us a place to document
// supported constructor parameters which are not common to all formats.
public interface StructuredLoggingFormatter<E> {

	/**
	 * Formats the given log event.
	 * @param event the log event to write
	 * @return the formatted log event
	 */
	String format(E event);

}
