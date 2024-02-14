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

package org.springframework.boot.actuate.autoconfigure.tracing.zipkin;

import org.springframework.boot.web.client.RestTemplateBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link RestTemplateBuilder} used to send spans to Zipkin.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 * @deprecated since 3.3.0 for removal in 3.5.0 in favor of
 * {@link ZipkinHttpClientBuilderCustomizer}
 */
@FunctionalInterface
@Deprecated(since = "3.3.0", forRemoval = true)
public interface ZipkinRestTemplateBuilderCustomizer {

	/**
	 * Customize the rest template builder.
	 * @param restTemplateBuilder the {@code RestTemplateBuilder} to customize
	 * @return the customized {@code RestTemplateBuilder}
	 */
	RestTemplateBuilder customize(RestTemplateBuilder restTemplateBuilder);

}
