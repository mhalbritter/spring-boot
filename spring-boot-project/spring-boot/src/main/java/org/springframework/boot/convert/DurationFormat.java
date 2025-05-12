/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.convert;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Annotation that can be used to indicate the format to use when converting a
 * {@link Duration}.
 *
 * @author Phillip Webb
 * @since 2.0.0
 * @deprecated since 3.5.0 for removal in 4.0.0 in favor of {@link org.springframework.format.annotation.DurationFormat}.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated(since = "3.5.0", forRemoval = true)
@SuppressWarnings("removal")
public @interface DurationFormat {

	/**
	 * The duration format style.
	 * @return the duration format style.
	 */
	DurationStyle value();

}
