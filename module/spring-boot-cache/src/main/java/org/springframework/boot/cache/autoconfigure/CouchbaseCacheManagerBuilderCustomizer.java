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

package org.springframework.boot.cache.autoconfigure;

import org.springframework.data.couchbase.cache.CouchbaseCacheManager;
import org.springframework.data.couchbase.cache.CouchbaseCacheManager.CouchbaseCacheManagerBuilder;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link CouchbaseCacheManagerBuilder} before it is used to build the auto-configured
 * {@link CouchbaseCacheManager}.
 *
 * @author Stephane Nicoll
 * @since 4.0.0
 */
@FunctionalInterface
public interface CouchbaseCacheManagerBuilderCustomizer {

	/**
	 * Customize the {@link CouchbaseCacheManagerBuilder}.
	 * @param builder the builder to customize
	 */
	void customize(CouchbaseCacheManagerBuilder builder);

}
