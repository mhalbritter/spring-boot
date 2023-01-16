/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.build.bom.bomr.cache;

/**
 * Http cache, which allows caching based on the etag header.
 *
 * @author Moritz Halbritter
 */
public interface HttpCache {

	/**
	 * Adds a new entry to the cache.
	 * @param uri the uri
	 * @param etag the etag
	 * @param content the content
	 */
	void store(String uri, String etag, String content);

	/**
	 * Looks up the cached entry for the given URI. Returns null if no such entry exists.
	 * @param uri the uri
	 * @return the cached entry, or null if no such entry exists
	 */
	Cached load(String uri);

	/**
	 * Cached entry.
	 */
	class Cached {

		private final String etag;

		private final String content;

		public Cached(String etag, String content) {
			this.etag = etag;
			this.content = content;
		}

		/**
		 * Gets the etag of the cached entry.
		 * @return etag
		 */
		public String getEtag() {
			return this.etag;
		}

		/**
		 * Gets the content of the cached entry.
		 * @return content
		 */
		public String getContent() {
			return this.content;
		}

	}

}
