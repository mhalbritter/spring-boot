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

package org.springframework.boot.autoconfigure;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Provides access to meta-data written by the auto-configure annotation processor.
 *
 * @author Phillip Webb
 * @since 1.5.0
 */
public interface AutoConfigurationMetadata {

	/**
	 * Return {@code true} if the specified class name was processed by the annotation
	 * processor.
	 * @param className the source class
	 * @return if the class was processed
	 */
	boolean wasProcessed(String className);

	/**
	 * Get an {@link Integer} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @return the meta-data value or {@code null}
	 */
	@Nullable Integer getInteger(String className, String key);

	/**
	 * Get an {@link Integer} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @param defaultValue the default value
	 * @return the meta-data value or {@code defaultValue}
	 */
	@Contract("_, _, !null -> !null")
	@Nullable Integer getInteger(String className, String key, @Nullable Integer defaultValue);

	/**
	 * Get a {@link Set} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @return the meta-data value or {@code null}
	 */
	@Nullable Set<String> getSet(String className, String key);

	/**
	 * Get a {@link Set} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @param defaultValue the default value
	 * @return the meta-data value or {@code defaultValue}
	 */
	@Contract("_, _, !null -> !null")
	@Nullable Set<String> getSet(String className, String key, @Nullable Set<String> defaultValue);

	/**
	 * Get an {@link String} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @return the meta-data value or {@code null}
	 */
	@Nullable String get(String className, String key);

	/**
	 * Get an {@link String} value from the meta-data.
	 * @param className the source class
	 * @param key the meta-data key
	 * @param defaultValue the default value
	 * @return the meta-data value or {@code defaultValue}
	 */
	@Contract("_, _, !null -> !null")
	@Nullable String get(String className, String key, @Nullable String defaultValue);

}
